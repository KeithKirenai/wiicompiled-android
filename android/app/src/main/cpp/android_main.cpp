#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <string>
#include <cstdlib>
#include <thread>
#include <atomic>
#include <chrono>
#include <cmath>
#include <algorithm>
#include <deque>
#include <fstream>

#include <unistd.h>
#include <sys/stat.h>
#include <mutex>

#include "android_touch_input.h"
#include "android_disc_extractor.h"
#include <aurora/gfx.h>
#include <aurora/cpu_topology.hpp>

#define LOG_TAG "WiiCompiled_NDK"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static ANativeWindow* g_nativeWindow = nullptr;
extern "C" void* g_mkwAndroidNativeWindow = nullptr;
static std::thread g_renderThread;
static std::atomic<bool> g_renderRunning{false};

// ---------------------------------------------------------------------------
// Lightweight frame-timing profiler
// Tracks the last 60 frame timestamps to compute rolling FPS and frame time.
// Called from the render thread after each presented frame.
// ---------------------------------------------------------------------------
namespace PerfStats {
    static std::mutex g_mutex;
    static std::deque<std::chrono::steady_clock::time_point> g_frameTimes;
    constexpr int kWindow = 60;

    static float g_fps = 0.f;
    static float g_frameTimeMs = 0.f;

    void recordFrame() {
        auto now = std::chrono::steady_clock::now();
        std::lock_guard<std::mutex> lock(g_mutex);
        g_frameTimes.push_back(now);
        if ((int)g_frameTimes.size() > kWindow) {
            g_frameTimes.pop_front();
        }
        if (g_frameTimes.size() >= 2) {
            double totalMs = std::chrono::duration<double, std::milli>(
                g_frameTimes.back() - g_frameTimes.front()).count();
            double frames = (double)(g_frameTimes.size() - 1);
            g_frameTimeMs = (float)(totalMs / frames);
            g_fps = (float)(frames / (totalMs / 1000.0));
        }
    }

    void getStats(float& fps, float& frameTimeMs) {
        std::lock_guard<std::mutex> lock(g_mutex);
        fps = g_fps;
        frameTimeMs = g_frameTimeMs;
    }
} // namespace PerfStats

// Forward declaration of pipeline queued count from aurora
namespace aurora::gfx {
uint32_t queued_pipeline_count() noexcept;
} // namespace aurora::gfx

namespace aurora::window {
void set_surface_ready(bool ready) noexcept;
}

static void StartLogcatRedirect() {
    static std::once_flag s_once;
    std::call_once(s_once, []() {
        int pfd[2];
        if (pipe(pfd) == 0) {
            dup2(pfd[1], fileno(stdout));
            dup2(pfd[1], fileno(stderr));
            setvbuf(stdout, nullptr, _IONBF, 0);
            setvbuf(stderr, nullptr, _IONBF, 0);
            std::thread([readFd = pfd[0]]() {
                char buf[1024];
                ssize_t r;
                std::string line;
                while ((r = read(readFd, buf, sizeof(buf) - 1)) > 0) {
                    buf[r] = '\0';
                    line += buf;
                    size_t pos;
                    while ((pos = line.find('\n')) != std::string::npos) {
                        std::string sub = line.substr(0, pos);
                        __android_log_print(ANDROID_LOG_INFO, "WiiCompiled_NDK", "%s", sub.c_str());
                        line.erase(0, pos + 1);
                    }
                }
            }).detach();
        }
    });
}

// Aurora frame-worker-wait callback: called by the render thread every time
// Aurora has submitted a frame and is waiting for the async worker.
// We record it as a presented frame for FPS / frame-time tracking.
extern "C" void AndroidOnFrameWait() {
    PerfStats::recordFrame();

    // Periodic logcat dump (every ~5 seconds at 60 fps = 300 frames)
    static int s_frameCount = 0;
    if (++s_frameCount >= 300) {
        s_frameCount = 0;
        float fps = 0.f, ftMs = 0.f;
        PerfStats::getStats(fps, ftMs);
        uint32_t queued = 0;
        try { queued = aurora::gfx::queued_pipeline_count(); } catch (...) {}
        LOGI("[Profile] FPS=%.1f  FrameTime=%.2fms  QueuedShaders=%u", fps, ftMs, queued);
    }
}

extern int RuntimeMain(int argc, char** argv);

#include <sys/resource.h>
#include <sched.h>

static void GameRenderWorker() {
    LOGI("GameRenderWorker started - invoking WiiCompiled RuntimeMain");
    if (!g_nativeWindow) {
        LOGE("GameRenderWorker started without native window");
        return;
    }

    // Dynamically pin game execution thread to performance/prime cores and elevate priority
    aurora::cpu::pin_to_performance_cores(-19);
    LOGI("GameRenderWorker thread pinned via dynamic CPU topology");

    g_mkwAndroidNativeWindow = g_nativeWindow;

    char arg0[] = "WiiCompiled";
    char* argv[] = { arg0, nullptr };

    try {
        int result = RuntimeMain(1, argv);
        LOGI("RuntimeMain returned with exit code: %d", result);
    } catch (const std::exception& ex) {
        LOGE("RuntimeMain threw unhandled exception: %s", ex.what());
    } catch (...) {
        LOGE("RuntimeMain threw unknown non-standard exception");
    }
    LOGI("GameRenderWorker exited");
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_wiicompiled_mkw_GameActivity_nativeInit(JNIEnv* env, jobject thiz, jstring internalPath) {
    StartLogcatRedirect();
    const char* pathStr = env->GetStringUTFChars(internalPath, nullptr);
    LOGI("Native init with data directory: %s", pathStr);
    setenv("INTERNAL_STORAGE", pathStr, 1);
    setenv("XDG_DATA_HOME", pathStr, 1);
    setenv("HOME", pathStr, 1);

    // Write an Android-optimized Config.toml only if none exists yet.
    std::string wiicompiledDir = std::string(pathStr) + "/WiiCompiled";
    std::string configPath = wiicompiledDir + "/Config.toml";
    ::mkdir(wiicompiledDir.c_str(), 0755);
    if (!std::filesystem::exists(configPath)) {
        std::ofstream cfg(configPath);
        if (cfg) {
            cfg << "# WiiCompiled Android configuration (optimized)\n"
                   "\n"
                   "[paths]\n"
                   "dvd_root = \"" << pathStr << "/game_data\"\n"
                   "\n"
                   "[video]\n"
                   "widescreen = true\n"
                   "resolution_multiplier = 1.0\n"
                   "frame_interpolation_fps = 0\n"
                   "display_mode = \"windowed\"\n"
                   "graphics_api = \"vulkan\"\n"
                   "skip_unready_pipelines = true\n"
                   "disable_copy_filter = true\n"
                   "show_fps = false\n"
                   "texture_replacements = false\n"
                   "texture_dumps = false\n"
                   "\n"
                   "[audio]\n"
                   "volume = 1.0\n"
                   "music_volume = 1.0\n"
                   "sound_effects_volume = 1.0\n"
                   "ui_volume = 1.0\n"
                   "voices_volume = 1.0\n"
                   "muted = false\n"
                   "mix_worker = true\n"
                   "\n"
                   "[network]\n"
                   "enabled = false\n"
                   "\n"
                   "[discord]\n"
                   "enabled = false\n";
            LOGI("Wrote initial Android Config.toml to %s", configPath.c_str());
        }
    } else {
        LOGI("Found existing Config.toml at %s (preserving launcher settings)", configPath.c_str());
    }
    aurora_set_disable_copy_filter(true);
    env->ReleaseStringUTFChars(internalPath, pathStr);
}

extern "C" void Android_SetScreenResolution(int surfaceWidth, int surfaceHeight, int deviceWidth, int deviceHeight, float density, float rate);


JNIEXPORT void JNICALL
Java_com_wiicompiled_mkw_GameActivity_nativeSurfaceCreated(JNIEnv* env, jobject thiz, jobject surface) {
    g_nativeWindow = ANativeWindow_fromSurface(env, surface);
    g_mkwAndroidNativeWindow = g_nativeWindow;
    LOGI("Native window acquired: %p", g_nativeWindow);

    jclass sdlClass = env->FindClass("org/libsdl/app/SDLActivity");
    if (sdlClass) {
        jfieldID fid = env->GetStaticFieldID(sdlClass, "mCustomSurface", "Landroid/view/Surface;");
        if (fid) {
            env->SetStaticObjectField(sdlClass, fid, surface);
            LOGI("Assigned SDLActivity.mCustomSurface via JNI");
        }
        env->DeleteLocalRef(sdlClass);
    }
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }

    if (g_nativeWindow) {
        int32_t w = ANativeWindow_getWidth(g_nativeWindow);
        int32_t h = ANativeWindow_getHeight(g_nativeWindow);
        if (w <= 0 || h <= 0) {
            w = 1280;
            h = 720;
        }
        Android_SetScreenResolution(w, h, w, h, 1.0f, 60.0f);
        LOGI("Configured SDL surface resolution: %dx%d", w, h);
    }

    aurora::window::set_surface_ready(true);

    if (g_nativeWindow && !g_renderRunning.load()) {
        g_renderRunning.store(true);
        if (g_renderThread.joinable()) {
            g_renderThread.join();
        }
        g_renderThread = std::thread(GameRenderWorker);
    }
}

JNIEXPORT void JNICALL
Java_com_wiicompiled_mkw_GameActivity_nativeSurfaceDestroyed(JNIEnv* env, jobject thiz) {
    LOGI("Native window releasing: %p", g_nativeWindow);
    aurora::window::set_surface_ready(false);
    jclass sdlClass = env->FindClass("org/libsdl/app/SDLActivity");
    if (sdlClass) {
        jfieldID fid = env->GetStaticFieldID(sdlClass, "mCustomSurface", "Landroid/view/Surface;");
        if (fid) {
            env->SetStaticObjectField(sdlClass, fid, nullptr);
        }
        env->DeleteLocalRef(sdlClass);
    }
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    g_renderRunning.store(false);
    if (g_renderThread.joinable()) {
        g_renderThread.join();
    }
    if (g_nativeWindow) {
        ANativeWindow_release(g_nativeWindow);
        g_nativeWindow = nullptr;
    }
}

JNIEXPORT void JNICALL
Java_com_wiicompiled_mkw_GameActivity_nativeSetButton(JNIEnv* env, jobject thiz, jint buttonId, jboolean isPressed) {
    LOGI("nativeSetButton: buttonId=%d isPressed=%d", (int)buttonId, (int)isPressed);
    switch (buttonId) {
        case 0: // BTN_A (Gas / Accelerate / Confirm)
            AndroidInput::SetButton(AndroidInput::kBtnA, isPressed);
            break;
        case 1: // BTN_B (Drift / Hop / Brake / Cancel)
            AndroidInput::SetButton(AndroidInput::kBtnB, isPressed);
            break;
        case 2: // BTN_L (Use Item)
            AndroidInput::SetButton(AndroidInput::kBtnL, isPressed);
            break;
        case 3: // BTN_START (Pause / Plus)
            AndroidInput::SetButton(AndroidInput::kBtnPlus, isPressed);
            break;
        case 4: // BTN_X
            AndroidInput::SetButton(AndroidInput::kBtnX, isPressed);
            break;
        case 5: // BTN_Y
            AndroidInput::SetButton(AndroidInput::kBtnY, isPressed);
            break;
        case 6: // BTN_R
            AndroidInput::SetButton(AndroidInput::kBtnR, isPressed);
            break;
        case 7: // BTN_ZL / Left Trigger
            AndroidInput::SetButton(AndroidInput::kBtnZL, isPressed);
            break;
        case 8: // BTN_ZR / Right Trigger (Gas alternative on Xbox RT)
            AndroidInput::SetButton(AndroidInput::kBtnZR, isPressed);
            break;
        case 9: // BTN_SELECT / Minus
            AndroidInput::SetButton(AndroidInput::kBtnMinus, isPressed);
            break;
        case 10: // DPAD_UP
            AndroidInput::SetButton(AndroidInput::kBtnDpadUp, isPressed);
            break;
        case 11: // DPAD_DOWN
            AndroidInput::SetButton(AndroidInput::kBtnDpadDown, isPressed);
            break;
        case 12: // DPAD_LEFT
            AndroidInput::SetButton(AndroidInput::kBtnDpadLeft, isPressed);
            break;
        case 13: // DPAD_RIGHT
            AndroidInput::SetButton(AndroidInput::kBtnDpadRight, isPressed);
            break;
        default:
            break;
    }
}

static std::atomic<bool> g_touchStickActive{false};

JNIEXPORT void JNICALL
Java_com_wiicompiled_mkw_GameActivity_nativeSetStick(JNIEnv* env, jobject thiz, jfloat stickX, jfloat stickY) {
    if (std::abs(stickX) > 0.05f || std::abs(stickY) > 0.05f) {
        g_touchStickActive.store(true);
    } else {
        g_touchStickActive.store(false);
    }
    int8_t rawX = static_cast<int8_t>(std::clamp(stickX * 127.0f, -128.0f, 127.0f));
    int8_t rawY = static_cast<int8_t>(std::clamp(stickY * 127.0f, -128.0f, 127.0f));
    AndroidInput::SetStick(rawX, rawY);
}

JNIEXPORT void JNICALL
Java_com_wiicompiled_mkw_GameActivity_nativeTouchEvent(JNIEnv* env, jobject thiz, jint action, jfloat x, jfloat y, jint pointerId) {
    bool isDown = (action == 0 || action == 2);
    if (x > 1400.0f && y > 700.0f) {
        AndroidInput::SetButton(AndroidInput::kBtnA, isDown);
    } else if (x > 1400.0f && y <= 700.0f) {
        AndroidInput::SetButton(AndroidInput::kBtnB, isDown);
    } else if (x < 600.0f && y < 400.0f) {
        AndroidInput::SetButton(AndroidInput::kBtnL, isDown);
    }
}

JNIEXPORT void JNICALL
Java_com_wiicompiled_mkw_GameActivity_nativeTiltEvent(JNIEnv* env, jobject thiz, jfloat angle) {
    AndroidInput::SetTilt(angle);
    if (!g_touchStickActive.load()) {
        int8_t steer = static_cast<int8_t>(std::clamp(angle * 128.0f, -128.0f, 127.0f));
        AndroidInput::SetStick(steer, 0);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_wiicompiled_mkw_MainActivity_nativeExtractDisc(JNIEnv* env, jobject thiz, jstring discPath, jstring destDir) {
    const char* cDiscPath = env->GetStringUTFChars(discPath, nullptr);
    const char* cDestDir = env->GetStringUTFChars(destDir, nullptr);

    LOGI("Native extracting disc %s to %s", cDiscPath, cDestDir);

    auto result = AndroidDiscIO::ExtractDisc(cDiscPath, cDestDir, [](const std::string& status, float prog) {
        LOGI("Extracting: %s (%.1f%%)", status.c_str(), prog * 100.0f);
    });

    env->ReleaseStringUTFChars(discPath, cDiscPath);
    env->ReleaseStringUTFChars(destDir, cDestDir);

    if (!result.success) {
        LOGE("Extraction failed: %s", result.errorMessage.c_str());
    }
    return result.success ? JNI_TRUE : JNI_FALSE;
}

// Returns a performance stats string for the Android HUD.
// Format: "FPS: 59.8  |  16.7ms  |  Shaders: 0"
JNIEXPORT jstring JNICALL
Java_com_wiicompiled_mkw_GameActivity_nativeGetPerfStats(JNIEnv* env, jobject thiz) {
    float fps = 0.f, ftMs = 0.f;
    PerfStats::getStats(fps, ftMs);
    uint32_t queued = 0;
    try { queued = aurora::gfx::queued_pipeline_count(); } catch (...) {}

    char buf[128];
    if (fps < 1.f) {
        snprintf(buf, sizeof(buf), "FPS: --");
    } else {
        snprintf(buf, sizeof(buf), "FPS: %.0f  |  %.1fms  |  Shaders: %u",
                 fps, ftMs, queued);
    }
    return env->NewStringUTF(buf);
}

} // extern "C"
