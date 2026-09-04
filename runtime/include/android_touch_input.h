#pragma once

#include <cstdint>

namespace AndroidInput {

struct TouchInputState {
    int8_t stickX = 0;       // -128 to 127
    int8_t stickY = 0;       // -128 to 127
    uint32_t buttons = 0;    // Classic Controller buttons bitmask
    float tiltAngle = 0.0f;  // Radians, steering gyro/accelerometer
    bool touchActive = false;
};

// Classic Controller button bitmask constants (matching Wii SDK)
constexpr uint32_t kBtnDpadUp    = 0x00000001;
constexpr uint32_t kBtnDpadLeft  = 0x00000002;
constexpr uint32_t kBtnZR        = 0x00000004;
constexpr uint32_t kBtnX         = 0x00000008;
constexpr uint32_t kBtnA         = 0x00000010;
constexpr uint32_t kBtnY         = 0x00000020;
constexpr uint32_t kBtnB         = 0x00000040;
constexpr uint32_t kBtnZL        = 0x00000080;
constexpr uint32_t kBtnR         = 0x00000200;
constexpr uint32_t kBtnPlus      = 0x00000400;
constexpr uint32_t kBtnMinus     = 0x00001000;
constexpr uint32_t kBtnL         = 0x00002000;
constexpr uint32_t kBtnDpadDown  = 0x00004000;
constexpr uint32_t kBtnDpadRight = 0x00008000;

void SetTouchState(const TouchInputState& state);
TouchInputState GetTouchState();

void SetButton(uint32_t button, bool pressed);
void SetStick(int8_t x, int8_t y);
void SetTilt(float angle);

} // namespace AndroidInput
