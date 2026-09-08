# On-Device ARM64 Recompilation Pipeline (Mario Kart Wii)

## Pipeline Architecture & Status
1. **Translator Environment (Termux + PRoot Debian)**:
   - CoreCLR `.NET 8` runtime executing `Translator.Cli` directly on Android aarch64.
   - Fixed memory exhaustion by applying heap boundaries:
     `DOTNET_gcServer=0`, `DOTNET_GCHeapHardLimit=0x80000000`, `threads=4`.
2. **Translation & Shard Generation**:
   - Decompiled 29,637 functions in 225s.
   - Generated `data_sections_init.cpp`, `guest_symbol_table.cpp`, and 72 balanced compilation shards (`MKW_BASE_COMMON_SHARDS`).
3. **Compilation Pipeline**:
   - Native Termux Clang / Ninja compiling C++20 targets with `-O3 -DNDEBUG -fPIC`.
   - Generates `libmkw_base_shared.a` with strictly the 72 shards (excluding `data_sections_init.cpp` and `guest_symbol_table.cpp` which link directly in Android Studio/Gradle).

---

## Single Command: `build-mkwii.sh`

From a fresh Termux install on the phone, one script handles the entire pipeline end-to-end:

```bash
cd ~/wiicompiled-android/tools/android-on-device
chmod +x build-mkwii.sh
./build-mkwii.sh
```

`build-mkwii.sh` is idempotent and covers:

1. **Termux deps** — `pkg install` of `git clang cmake ninja proot-distro llvm-tools wget`.
2. **Debian PRoot + .NET 8** — installs the Debian rootfs if missing, provisions the Microsoft .NET 8 feed and `dotnet-sdk-8.0` inside it, no-op otherwise.
3. **Repository + Translator.Cli** — clones `wiicompiled-android` if absent, then `dotnet publish -c Release -r linux-arm64 --self-contained` for `Translator.Cli`; skips if already published.
4. **Asset prerequisites (manual)** — verifies `Assets/main.dol`, `Assets/StaticR.rel`, `projects/mkwii/MAP.txt`, `projects/mkwii/recomp.yml`. If `Assets/` is empty it tries copying from `/sdcard/Download/` as a convenience; otherwise it gives explicit copy commands and exits. **No ISO/WBFS extraction is attempted on-device.**
5. **Translate + shard emission** — runs `translate-recursive`, `generate-data-init`, and `emit-build-shards` under the memory-bound CoreCLR env.
6. **Compile + stage** — builds the 72-shard `libmkw_base_shared.a` via native Clang/Ninja, validates the object count with `llvm-objdump`, copies the archive to `dist/` and to `android/app/src/main/jniLibs/arm64-v8a/`.

---

## Asset Setup (manual, once)

This pipeline does **not** extract discs on the phone. Provide `main.dol` and `StaticR.rel` yourself:

```bash
# Option A — copy from phone Downloads (if you already have them there)
mkdir -p ~/wiicompiled-android/Assets
cp /sdcard/Download/main.dol ~/wiicompiled-android/Assets/
cp /sdcard/Download/StaticR.rel ~/wiicompiled-android/Assets/

# Option B — from anywhere else on the phone
cp /path/to/your/main.dol ~/wiicompiled-android/Assets/
cp /path/to/your/StaticR.rel ~/wiicompiled-android/Assets/
```

Get these from your own PAL `RMCP01` dump using Dolphin Android or a desktop extractor (e.g. cleanrip). The repo's desktop bootstrap (`android-bootstrap.bat -DiscSource ...`) uses its own internal `WiiDiscExtractor` for this on Windows; on Android, extraction is intentionally manual.

---

## What the script produces

- `~/wiicompiled-android/dist/libmkw_base_shared.a`
- `~/wiicompiled-android/android/app/src/main/jniLibs/arm64-v8a/libmkw_base_shared.a`

The staged archive is the one the Android Gradle app links against. After a successful run you can build/install the APK from the `android/` Gradle project as usual.
