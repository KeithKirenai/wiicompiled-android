#pragma once

#include <array>
#include <cstdint>
#include <filesystem>
#include <fstream>
#include <map>
#include <optional>
#include <string>
#include <utility>

namespace RuntimeNandSettings {

using Settings = std::map<std::string, std::string>;

// Wii setting.txt is a 256-byte buffer encrypted with a rotating XOR key.
inline std::optional<Settings> Read(const std::filesystem::path& nandRoot) {
    std::ifstream input(nandRoot / "title/00000001/00000002/data/setting.txt",
                        std::ios::binary);
    std::array<uint8_t, 256> bytes{};
    if (!input.read(reinterpret_cast<char*>(bytes.data()), bytes.size())) {
        return std::nullopt;
    }
    uint32_t key = 0x73B5DBFAu;
    std::string decoded;
    for (const uint8_t byte : bytes) {
        const char value = static_cast<char>(byte ^ static_cast<uint8_t>(key));
        key = (key << 1) | (key >> 31);
        if (value == '\0') {
            break;
        }
        if (value != '\r') {
            decoded += value;
        }
    }
    Settings settings;
    for (size_t start = 0; start < decoded.size();) {
        const size_t end = decoded.find('\n', start);
        const std::string line = decoded.substr(start, end - start);
        const size_t equals = line.find('=');
        if (equals != std::string::npos && equals != 0) {
            settings.emplace(line.substr(0, equals), line.substr(equals + 1));
        }
        if (end == std::string::npos) {
            break;
        }
        start = end + 1;
    }
    return settings;
}

inline bool HasIdentity(const Settings& settings) {
    const auto serial = settings.find("SERNO");
    if (serial == settings.end() || serial->second.empty() || serial->second.size() > 9 ||
        serial->second.find_first_not_of("0123456789") != std::string::npos ||
        serial->second.find_first_not_of('0') == std::string::npos) {
        return false;
    }
    for (const auto& field : {std::pair{"CODE", 5u}, {"AREA", 3u}, {"GAME", 2u}}) {
        const auto value = settings.find(field.first);
        if (value == settings.end() || value->second.empty() ||
            value->second.size() > field.second) {
            return false;
        }
    }
    return true;
}

inline bool Write(const std::filesystem::path& nandRoot, const Settings& settings) {
    const auto path = nandRoot / "title/00000001/00000002/data/setting.txt";
    std::error_code ec;
    std::filesystem::create_directories(path.parent_path(), ec);
    if (ec) {
        return false;
    }

    std::string plain;
    for (const auto& [key, value] : settings) {
        plain += key;
        plain += '=';
        plain += value;
        plain += "\r\n";
    }

    if (plain.size() >= 256) {
        return false;
    }

    std::array<uint8_t, 256> bytes{};
    uint32_t key = 0x73B5DBFAu;
    for (size_t i = 0; i < bytes.size(); ++i) {
        const char val = (i < plain.size()) ? plain[i] : '\0';
        bytes[i] = static_cast<uint8_t>(val) ^ static_cast<uint8_t>(key);
        key = (key << 1) | (key >> 31);
    }

    std::ofstream output(path, std::ios::binary | std::ios::trunc);
    if (!output) {
        return false;
    }
    output.write(reinterpret_cast<const char*>(bytes.data()), bytes.size());
    return output.good();
}

} // namespace RuntimeNandSettings
