#pragma once

#include <vector>
#include <string>
#include <fstream>
#include <algorithm>
#include <thread>
#include <cstdint>

#if defined(__linux__) || defined(__ANDROID__)
#include <unistd.h>
#include <sched.h>
#include <sys/syscall.h>
#include <sys/resource.h>
#include <android/log.h>
#endif

namespace aurora::cpu {

struct CoreInfo {
    int id = 0;
    uint64_t maxFreqKHz = 0;
};

struct Topology {
    int totalCores = 1;
    std::vector<int> littleCores;          // Efficiency cluster (Cortex-A55, etc.)
    std::vector<int> bigCores;             // Mid/Performance cluster (Cortex-A77/A78)
    std::vector<int> primeCores;           // Cortex-X1/Prime or fastest core(s)
    std::vector<int> allPerformanceCores;  // big + prime cores
};

inline Topology detect_topology() {
    Topology topo;
#if defined(__linux__) || defined(__ANDROID__)
    long nprocs = sysconf(_SC_NPROCESSORS_CONF);
    if (nprocs <= 0) {
        nprocs = sysconf(_SC_NPROCESSORS_ONLN);
    }
    if (nprocs <= 0) {
        nprocs = std::max<int>(1, std::thread::hardware_concurrency());
    }
    topo.totalCores = static_cast<int>(nprocs);

    std::vector<CoreInfo> cores;
    cores.reserve(nprocs);
    uint64_t minFreq = UINT64_MAX;
    uint64_t maxFreq = 0;

    for (int i = 0; i < topo.totalCores; ++i) {
        uint64_t freq = 0;
        std::string path = "/sys/devices/system/cpu/cpu" + std::to_string(i) + "/cpufreq/cpuinfo_max_freq";
        std::ifstream f(path);
        if (f.is_open()) {
            f >> freq;
        } else {
            // Fallback to scaling_max_freq
            std::string path2 = "/sys/devices/system/cpu/cpu" + std::to_string(i) + "/cpufreq/scaling_max_freq";
            std::ifstream f2(path2);
            if (f2.is_open()) {
                f2 >> freq;
            }
        }
        if (freq == 0) {
            // Sysfs unreadable fallback: divide lower half as efficiency, upper as performance
            freq = (i < topo.totalCores / 2) ? 1000000 : 2000000;
        }
        cores.push_back({i, freq});
        if (freq < minFreq) minFreq = freq;
        if (freq > maxFreq) maxFreq = freq;
    }

    if (minFreq == maxFreq || cores.empty()) {
        // Homogeneous topology (single frequency across all cores)
        for (int i = 0; i < topo.totalCores; ++i) {
            topo.allPerformanceCores.push_back(i);
            topo.bigCores.push_back(i);
        }
        topo.primeCores.push_back(topo.totalCores - 1);
        topo.littleCores.push_back(0);
    } else {
        for (const auto& c : cores) {
            if (c.maxFreqKHz <= minFreq * 1.1) {
                topo.littleCores.push_back(c.id);
            } else if (c.maxFreqKHz >= maxFreq * 0.98 && maxFreq > minFreq * 1.25) {
                topo.primeCores.push_back(c.id);
                topo.allPerformanceCores.push_back(c.id);
            } else {
                topo.bigCores.push_back(c.id);
                topo.allPerformanceCores.push_back(c.id);
            }
        }

        if (topo.allPerformanceCores.empty()) {
            for (int i = topo.totalCores / 2; i < topo.totalCores; ++i) {
                topo.allPerformanceCores.push_back(i);
                topo.bigCores.push_back(i);
            }
        }
        if (topo.littleCores.empty()) {
            for (int i = 0; i < topo.totalCores / 2; ++i) {
                topo.littleCores.push_back(i);
            }
        }
        if (topo.primeCores.empty() && !topo.bigCores.empty()) {
            topo.primeCores.push_back(topo.bigCores.back());
        }
    }

    std::string littleStr, bigStr, primeStr;
    for (int c : topo.littleCores) littleStr += std::to_string(c) + " ";
    for (int c : topo.bigCores) bigStr += std::to_string(c) + " ";
    for (int c : topo.primeCores) primeStr += std::to_string(c) + " ";
    __android_log_print(ANDROID_LOG_INFO, "WiiCompiled-CPU",
        "Dynamic Topology: Total=%d | Little=[%s] | Big=[%s] | Prime=[%s]",
        topo.totalCores, littleStr.c_str(), bigStr.c_str(), primeStr.c_str());
#else
    topo.totalCores = std::max<int>(1, std::thread::hardware_concurrency());
    for (int i = 0; i < topo.totalCores; ++i) {
        topo.allPerformanceCores.push_back(i);
        topo.bigCores.push_back(i);
    }
    topo.primeCores.push_back(topo.totalCores - 1);
    topo.littleCores.push_back(0);
#endif
    return topo;
}

inline const Topology& get_topology() {
    static Topology s_topology = detect_topology();
    return s_topology;
}

inline bool bind_thread_to_cores(const std::vector<int>& coreIds) {
#if defined(__linux__) || defined(__ANDROID__)
    if (coreIds.empty()) return false;
    cpu_set_t set;
    CPU_ZERO(&set);
    for (int c : coreIds) {
        if (c >= 0 && c < CPU_SETSIZE) {
            CPU_SET(c, &set);
        }
    }
    pid_t tid = static_cast<pid_t>(syscall(__NR_gettid));
    return syscall(__NR_sched_setaffinity, tid, sizeof(set), &set) == 0;
#else
    (void)coreIds;
    return false;
#endif
}

inline void set_thread_priority(int niceValue) {
#if defined(__linux__) || defined(__ANDROID__)
    pid_t tid = static_cast<pid_t>(syscall(__NR_gettid));
    setpriority(PRIO_PROCESS, tid, niceValue);
#else
    (void)niceValue;
#endif
}

inline void pin_to_performance_cores(int niceValue = -18) {
    const auto& topo = get_topology();
    bind_thread_to_cores(topo.allPerformanceCores);
    set_thread_priority(niceValue);
}

inline void pin_to_prime_core(int niceValue = -20) {
    const auto& topo = get_topology();
    bind_thread_to_cores(topo.primeCores);
    set_thread_priority(niceValue);
}

inline void pin_to_efficiency_cores(int niceValue = 10) {
    const auto& topo = get_topology();
    bind_thread_to_cores(topo.littleCores);
    set_thread_priority(niceValue);
}

inline void pin_to_audio_cores(int niceValue = -10) {
    const auto& topo = get_topology();
    if (!topo.bigCores.empty()) {
        bind_thread_to_cores(topo.bigCores);
    } else {
        bind_thread_to_cores(topo.allPerformanceCores);
    }
    set_thread_priority(niceValue);
}

} // namespace aurora::cpu
