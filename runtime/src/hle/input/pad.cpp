#include "hle_stubs.h"
#include "memory.h"
#include "hle/controller_status_contract.h"
#include "wii_remote_input.h"
#include "android_touch_input.h"

#include <algorithm>
#include <cstdio>
#include <cstdint>
#include <cstdlib>
#include <cstring>

#include <dolphin/pad.h>

namespace {

void WritePadStatus(uint32_t base, const PADStatus& status) {
    const auto guestStatus = PadStatusContract::Encode({
        status.button,
        status.stickX,
        status.stickY,
        status.substickX,
        status.substickY,
        status.triggerL,
        status.triggerR,
        status.analogA,
        status.analogB,
        status.err,
    });
    uint8_t* dst = Memory::GetPointer(base, guestStatus.size());
    std::memcpy(dst, guestStatus.data(), guestStatus.size());
}

} // namespace

extern "C" uint32_t PAD__Init_HLE()
{
    return PADInit() ? 1u : 0u;
}
PPC_NATIVE_OVERRIDE(801AF2F0, PAD__Init_HLE, uint32_t, (), ());

// PADRead: gathers every GameCube pad source for the frame and writes the statuses to guest memory.
extern "C" uint32_t PAD__Read_HLE(uint32_t statusPtr)
{
    if (statusPtr == 0) {
        return 0;
    }

    PADStatus statuses[PAD_CHANMAX]{};
    WiiRemoteInput::Poll();
    uint32_t rumbleMask = PADRead(statuses);
    // Wii Remotes reach the game through KPAD, not as GameCube pads. This also
    // applies while input is blocked (overlay open) so the port does not flip
    // between "connected" and "no controller" every time the overlay toggles.
    WiiRemoteInput::HideRemotesFromPad(statuses, PAD_CHANMAX);

#if defined(__ANDROID__)
    // On Android, always provide touch input as an active GameCube controller on Port 0
    auto touch = AndroidInput::GetTouchState();
    statuses[0].err = PAD_ERR_NONE;

    if (touch.buttons & AndroidInput::kBtnA) {
        statuses[0].button |= PAD_BUTTON_A;
        statuses[0].analogA = 255;
    }
    if (touch.buttons & AndroidInput::kBtnB) {
        statuses[0].button |= PAD_BUTTON_B;
        statuses[0].analogB = 255;
    }
    if (touch.buttons & AndroidInput::kBtnL) {
        statuses[0].button |= (PAD_TRIGGER_L | PAD_BUTTON_X);
        statuses[0].triggerL = 255;
    }
    if (touch.buttons & AndroidInput::kBtnR) {
        statuses[0].button |= PAD_TRIGGER_R;
        statuses[0].triggerR = 255;
    }
    if (touch.buttons & AndroidInput::kBtnPlus) {
        statuses[0].button |= PAD_BUTTON_START;
    }
    if (touch.buttons & AndroidInput::kBtnDpadUp) statuses[0].button |= PAD_BUTTON_UP;
    if (touch.buttons & AndroidInput::kBtnDpadDown) statuses[0].button |= PAD_BUTTON_DOWN;
    if (touch.buttons & AndroidInput::kBtnDpadLeft) statuses[0].button |= PAD_BUTTON_LEFT;
    if (touch.buttons & AndroidInput::kBtnDpadRight) statuses[0].button |= PAD_BUTTON_RIGHT;

    statuses[0].stickX = touch.stickX;
    statuses[0].stickY = touch.stickY;
#endif

    try {
        for (uint32_t i = 0; i < PAD_CHANMAX; ++i) {
            WritePadStatus(statusPtr + static_cast<uint32_t>(i * PadStatusContract::kGuestStatusSize),
                           statuses[i]);
        }
    } catch (const Memory::AccessViolation&) {
        return 0;
    }

    return rumbleMask;
}
PPC_NATIVE_OVERRIDE(801AF44C, PAD__Read_HLE, uint32_t, (uint32_t statusPtr), (statusPtr));

extern "C" uint32_t PAD__Reset_HLE(uint32_t mask)
{
    return PADReset(mask) ? 1u : 0u;
}
PPC_NATIVE_OVERRIDE(801AF0DC, PAD__Reset_HLE, uint32_t, (uint32_t mask), (mask));

extern "C" uint32_t PAD__Recalibrate_HLE(uint32_t mask)
{
    return PADRecalibrate(mask) ? 1u : 0u;
}
PPC_NATIVE_OVERRIDE(801AF1E4, PAD__Recalibrate_HLE, uint32_t, (uint32_t mask), (mask));

extern "C" void PAD__ControlMotor_HLE(int32_t chan, uint32_t command)
{
    PADControlMotor(chan, command);
}
PPC_NATIVE_OVERRIDE_VOID(801AF908, PAD__ControlMotor_HLE, (int32_t chan, uint32_t command), (chan, command));
