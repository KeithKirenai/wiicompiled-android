#ifndef AURORA_GFX_H
#define AURORA_GFX_H

#ifdef __cplusplus
#include <cstddef>
#include <cstdint>

extern "C" {
#else
#include "stddef.h"
#include "stdint.h"
#endif

#ifndef NDEBUG
#define AURORA_GFX_DEBUG_GROUPS
#endif

void aurora_push_debug_group(const char* label);
void aurora_pop_debug_group();

typedef struct {
  uint32_t queuedPipelines;
  uint32_t createdPipelines;
  uint32_t drawCallCount;
  uint32_t mergedDrawCallCount;
  uint32_t lastVertSize;
  uint32_t lastUniformSize;
  uint32_t lastIndexSize;
  uint32_t lastStorageSize;
  uint32_t lastTextureUploadSize;
  uint32_t presentedFrameCount;
  uint32_t interpolatedFrameCount;
} AuroraStats;

// Dawn Vulkan pipeline-cache (dawn_cache.db) replay accumulators, per process. When
// `hits` reaches `lookups` and `stores` stays 0, every queued pipeline build is a
// cached replay of a precompiled driver blob - not a shader compilation.
typedef struct {
  uint64_t lookups;
  uint64_t hits;
  uint64_t stores;
  uint64_t hitBytes;
} AuroraBlobCacheStats;

typedef struct {
  uint64_t totalPresentCount;
  uint32_t sampleCount;
  double framesPerSecond;
  double averageFrameTimeMs;
  double p95FrameTimeMs;
  double jitterMs;
  // framesPerSecond with duplicated presentation slots scaled out, so this is the rate of frames
  // that carried new motion. Equal to framesPerSecond when every slot replayed real interpolation.
  double effectiveFramesPerSecond;
} AuroraPresentTiming;

const AuroraStats* aurora_get_stats();
const AuroraBlobCacheStats* aurora_get_blob_cache_stats();
void aurora_get_present_timing(AuroraPresentTiming* timing);

// Per-pass CPU command-encoding breakdown of the latest native frame (driver-independent; the
// devices this targets are tiled GPUs whose drivers do not expose Vulkan timestamp queries, and on
// a tiler recorded order does not reflect real GPU time anyway). `totalUs` spans the whole native
// render; `passUs` covers per-pass encoding incl. its resolve/copy encodes, `passWidth`/`passHeight`
// the pass target size, and `passDraws` the number of draw commands in the pass. Pass order is
// submission order; arrays fill up to AURORA_GPU_PASS_TIMING_MAX, the rest stay 0.
#define AURORA_GPU_PASS_TIMING_MAX 16
typedef struct {
  uint32_t count;
  uint64_t totalUs;
  uint32_t passUs[AURORA_GPU_PASS_TIMING_MAX];
  uint32_t passWidth[AURORA_GPU_PASS_TIMING_MAX];
  uint32_t passHeight[AURORA_GPU_PASS_TIMING_MAX];
  uint32_t passDraws[AURORA_GPU_PASS_TIMING_MAX];
} AuroraGpuPassTimings;
void aurora_get_gpu_pass_timings(AuroraGpuPassTimings* timings);

// Interpolation health: the per-frame fields describe the last sealed frame, the counters
// accumulate since it was configured. This answers "output FPS dropped but the game held 60".
typedef struct {
  uint32_t targetFps;          // configured target, 0 when interpolation is off
  uint32_t targetSamples;      // slots the pacing controller currently aims for
  uint32_t activeSamples;      // slots latched for the latest sealed frame
  uint32_t candidates;         // perspective draws in the latest sealed frame
  uint32_t matchable;          // candidates whose identity also existed last frame
  uint32_t matches;            // draws matched to the previous frame
  uint32_t eligible;           // latest frame inserted interpolated slots
  uint32_t replaySafe;         // latest frame could replay its command stream
  uint64_t framesSealed;
  uint64_t framesLowMatch;
  uint64_t framesReplayUnsafe;
  uint64_t slotReductions;
  uint64_t lateSealDrops;
} AuroraFrameInterpolationDiagnostics;

void aurora_get_frame_interpolation_diagnostics(AuroraFrameInterpolationDiagnostics* diagnostics);

// Generates transform-interpolated perspective frames between consecutive 60 Hz logical frames.
// Supported targets are 0 (off), 120, 180 and 240. Guest simulation and VI timing are unchanged.
void aurora_set_frame_interpolation_fps(uint32_t targetFps);
uint32_t aurora_get_frame_interpolation_fps();

// Newly encountered GX pipelines compile on the bounded worker queue. Draws whose pipeline is not
// ready are skipped rather than stalling submission, and pick it up once compilation finishes.
void aurora_set_skip_unready_pipelines(bool enabled);
bool aurora_get_skip_unready_pipelines();
uint32_t aurora_get_queued_pipeline_count();

// Controls whether display copies bypass the Wii's vertical copy filter.
void aurora_set_disable_copy_filter(bool disabled);
bool aurora_get_disable_copy_filter();

// Guest-RAM write tracking. `generation` changes whenever guest RAM covering a host range was
// written (or returns AURORA_GUEST_WRITE_UNTRACKED); `notify` reports writes aurora made itself.
#define AURORA_GUEST_WRITE_UNTRACKED UINT64_MAX
typedef uint64_t (*AuroraGuestWriteGenerationCallback)(const void* hostPtr, size_t size);
typedef void (*AuroraGuestWriteNotifyCallback)(const void* hostPtr, size_t size);
void aurora_set_guest_write_hooks(AuroraGuestWriteGenerationCallback generation,
                                  AuroraGuestWriteNotifyCallback notify);

#ifdef __cplusplus
}
#endif

#endif
