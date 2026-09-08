# On-Device ARM64 Recompilation Pipeline (Mario Kart Wii)

## Current Status (Working & Verified)
1. **Translator Environment (Termux + PRoot Debian)**:
   - CoreCLR `.NET 8` runtime executing `Translator.Cli` directly on Android aarch64.
   - Fixed memory exhaustion by applying heap boundaries:
     `DOTNET_gcServer=0`, `DOTNET_GCHeapHardLimit=0x80000000`, `threads=4`.
2. **Translation & Shard Generation**:
   - Decompiled 29,637 functions in 225s.
   - Generated `data_sections_init.cpp`, `guest_symbol_table.cpp`, and 72 balanced compilation shards (`MKW_BASE_COMMON_SHARDS`).
3. **Compilation Pipeline**:
   - Native Clang 21.1.8 / Ninja compiling C++20 targets with `-O3 -DNDEBUG -fPIC`.
   - Verified initial `libmkw_base_shared.a` creation and valid `elf64-littleaarch64` ABI.

## Pending Items
1. **Archive Target Refinement**:
   - `libmkw_base_shared.a` must strictly contain the 72 `MKW_BASE_COMMON_SHARDS`.
   - `data_sections_init.cpp`, `guest_symbol_table.cpp`, and `base_registration/` must NOT be bundled in `.a` because `android/app/src/main/cpp/CMakeLists.txt` links them directly, avoiding duplicate symbols under `--whole-archive`.
2. **Build Resumption**:
   - Re-run `ninja -j4` on phone with updated `CMakeLists.txt` to produce the 72-member archive.
   - Copy to `android/app/src/main/jniLibs/arm64-v8a/libmkw_base_shared.a` and verify clean link of `libmkw_android.so`.
