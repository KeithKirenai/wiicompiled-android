// The PowerPC ISA package (runtime/include/isa/), re-exported under the single
// header name the generated code and the runtime include. The package has two
// host seams this project satisfies elsewhere: "ppc_isa_memory.h" (pulled by
// the quantized tier) and the ShowRuntimeFatalPopup implementation.

#pragma once

#include "isa/ppc_isa_config.h"
#include "isa/big_endian.h"
#include "isa/ppc_isa_fpenv.h"
#include "isa/ppc_isa_context.h"
#include "isa/ppc_isa_int.h"
#include "isa/ppc_isa_float.h"
#include "isa/ppc_isa_quantized.h"

// Guest-clock pause control (Android backgrounding). RuntimeSetGuestPaused(true)
// freezes the emulated time base so no elapsed wall time reaches guest timers,
// alarms or retrace delivery; the resume path slides the clock anchor forward by
// the paused duration so guest time continues exactly where it froze.
// Implemented in runtime/src/ppc_helpers.cpp.
extern "C" {
void RuntimeSetGuestPaused(bool paused) noexcept;
bool RuntimeIsGuestPaused() noexcept;
}
