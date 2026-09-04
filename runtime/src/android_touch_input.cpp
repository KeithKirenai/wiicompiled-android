#include "android_touch_input.h"

#include <atomic>
#include <mutex>

namespace AndroidInput {

namespace {
std::mutex g_inputMutex;
TouchInputState g_state;
std::atomic<bool> g_hasActiveInput{false};
}

void SetTouchState(const TouchInputState& state) {
    std::lock_guard<std::mutex> lock(g_inputMutex);
    g_state = state;
    g_hasActiveInput.store(state.touchActive, std::memory_order_relaxed);
}

TouchInputState GetTouchState() {
    std::lock_guard<std::mutex> lock(g_inputMutex);
    return g_state;
}

void SetButton(uint32_t button, bool pressed) {
    std::lock_guard<std::mutex> lock(g_inputMutex);
    if (pressed) {
        g_state.buttons |= button;
    } else {
        g_state.buttons &= ~button;
    }
    g_state.touchActive = true;
    g_hasActiveInput.store(true, std::memory_order_relaxed);
}

void SetStick(int8_t x, int8_t y) {
    std::lock_guard<std::mutex> lock(g_inputMutex);
    g_state.stickX = x;
    g_state.stickY = y;
    // Map stick to D-pad for menu navigation
    g_state.buttons &= ~(kBtnDpadUp | kBtnDpadDown | kBtnDpadLeft | kBtnDpadRight);
    if (y > 45) g_state.buttons |= kBtnDpadUp;
    if (y < -45) g_state.buttons |= kBtnDpadDown;
    if (x > 45) g_state.buttons |= kBtnDpadRight;
    if (x < -45) g_state.buttons |= kBtnDpadLeft;
    g_state.touchActive = true;
    g_hasActiveInput.store(true, std::memory_order_relaxed);
}

void SetTilt(float angle) {
    std::lock_guard<std::mutex> lock(g_inputMutex);
    g_state.tiltAngle = angle;
}

} // namespace AndroidInput
