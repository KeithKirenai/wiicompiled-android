#pragma once

// Ported from KartPad's wiicompiled-mii-seed.patch and seed_mii_database.h.
// The seed format and default Mii layout are retained for offline compatibility.

#include <array>
#include <cstddef>
#include <cstdint>
#include <span>
#include <string_view>
#include <vector>

namespace RuntimeMii {

inline constexpr std::size_t kDatabaseSize = 779968;
inline constexpr std::size_t kMiiBlockOffset = 0x04;
inline constexpr std::size_t kMiiBlockSize = 74;
inline constexpr std::size_t kDatabaseCrcOffset = 0x1F1DE;

inline uint16_t Crc16Ccitt(std::span<const uint8_t> bytes) {
    uint16_t crc = 0;
    for (const uint8_t byte : bytes) {
        crc ^= static_cast<uint16_t>(byte) << 8;
        for (int bit = 0; bit < 8; ++bit) {
            crc = (crc & 0x8000u) != 0
                ? static_cast<uint16_t>((crc << 1) ^ 0x1021u)
                : static_cast<uint16_t>(crc << 1);
        }
    }
    return crc;
}

inline void WriteBigEndian16(std::span<uint8_t> bytes, std::size_t offset, uint16_t value) {
    bytes[offset] = static_cast<uint8_t>(value >> 8);
    bytes[offset + 1] = static_cast<uint8_t>(value);
}

inline void WriteBigEndian32(std::span<uint8_t> bytes, std::size_t offset, uint32_t value) {
    bytes[offset] = static_cast<uint8_t>(value >> 24);
    bytes[offset + 1] = static_cast<uint8_t>(value >> 16);
    bytes[offset + 2] = static_cast<uint8_t>(value >> 8);
    bytes[offset + 3] = static_cast<uint8_t>(value);
}

inline void WriteMiiName(std::span<uint8_t> block, std::size_t offset, std::string_view ascii) {
    constexpr std::size_t kMaximumCharacters = 10;
    std::fill(block.begin() + offset, block.begin() + offset + kMaximumCharacters * 2, 0);
    for (std::size_t index = 0; index < ascii.size() && index < kMaximumCharacters; ++index) {
        block[offset + index * 2 + 1] = static_cast<uint8_t>(ascii[index]);
    }
}

inline std::array<uint8_t, kMiiBlockSize> CreateDefaultMii(const std::array<uint8_t, 6>& mac) {
    std::array<uint8_t, kMiiBlockSize> block{};
    std::span<uint8_t> bytes(block);
    WriteBigEndian16(bytes, 0x00, static_cast<uint16_t>((1u << 10) | (1u << 5)));
    WriteMiiName(bytes, 0x02, "Wiicompiled");
    bytes[0x16] = 63;
    bytes[0x17] = 63;
    WriteBigEndian32(bytes, 0x18, 0x80000001u);
    bytes[0x1C] = static_cast<uint8_t>(mac[0] + mac[1] + mac[2]);
    bytes[0x1D] = mac[3];
    bytes[0x1E] = mac[4];
    bytes[0x1F] = mac[5];
    WriteBigEndian16(bytes, 0x22, (33u << 9) | (1u << 6));
    WriteBigEndian32(bytes, 0x24, (6u << 27) | (6u << 22) | (1u << 13) | (4u << 9) | (10u << 4) | 2u);
    WriteBigEndian32(bytes, 0x28, (2u << 26) | (4u << 21) | (12u << 16) | (4u << 9) | (2u << 5));
    WriteBigEndian16(bytes, 0x2C, (1u << 12) | (4u << 8) | (9u << 3));
    WriteBigEndian16(bytes, 0x2E, (23u << 11) | (4u << 5) | 13u);
    WriteBigEndian16(bytes, 0x30, (4u << 5) | 10u);
    WriteBigEndian16(bytes, 0x32, (4u << 5) | 10u);
    WriteBigEndian16(bytes, 0x34, (4u << 11) | (20u << 6) | (2u << 1));
    WriteMiiName(bytes, 0x36, "Wiicompiled");
    return block;
}

inline std::vector<uint8_t> CreateSeedDatabase(const std::array<uint8_t, 6>& mac) {
    std::vector<uint8_t> database(kDatabaseSize);
    std::span<uint8_t> bytes(database);
    bytes[0] = 'R'; bytes[1] = 'N'; bytes[2] = 'O'; bytes[3] = 'D';
    const auto mii = CreateDefaultMii(mac);
    std::copy(mii.begin(), mii.end(), bytes.begin() + kMiiBlockOffset);
    bytes[0x1CE0 + 0x0C] = 0x80;
    bytes[0x1D00] = 'R'; bytes[0x1D01] = 'N'; bytes[0x1D02] = 'H'; bytes[0x1D03] = 'D';
    bytes[0x1D04] = 0xFF; bytes[0x1D05] = 0xFF; bytes[0x1D06] = 0xFF; bytes[0x1D07] = 0xFF;
    WriteBigEndian16(bytes, kDatabaseCrcOffset,
                     Crc16Ccitt(std::span<const uint8_t>(database.data(), kDatabaseCrcOffset)));
    return database;
}

} // namespace RuntimeMii
