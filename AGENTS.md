# AGENTS.md

Guidance for AI coding agents working in the **WiiCompiled Android Port** repository.

> The third-party license ledger and mandatory attributions for this project live in
> **[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)** — never edit it casually.

## Project Overview

This repository is the **Android port of WiiCompiled** (Mario Kart Wii PowerPC static recompilation): a Gradle app (`com.wiicompiled.mkw`) plus the native translate/build pipeline that plays Mario Kart Wii on Android (`arm64-v8a`) with no emulator.

## Repository Layout

* **`android/`** — The Android Studio Gradle application.
  * `app/src/main/java/com/wiicompiled/mkw/` — Kotlin/Java: disc importer & verification frontend, fullscreen surface, sensor manager (accelerometer steering), multitouch dispatcher.
  * `app/src/main/java/org/libsdl/` — SDL app glue.
  * `app/src/main/cpp/` — JNI native bridge compiled into `libmkw_android.so`.
  * `app/src/main/jniLibs/arm64-v8a/` — Prebuilt shard archive `libmkw_base_shared.a`.
  * `Config.toml` — App-level TOML configuration.
* **`cmake/shards/`** — CMake build for the translated shard archive.
* **`runtime/`**, **`translator/`**, **`aurora-main/`**, **`projects/`**, **`Assets/`** — Upstream WiiCompiled components.
* **Root build helpers** — `android-bootstrap.ps1/.bat`, `build-shards.bat`, `build-app*.bat`, `install-app.bat`, `run_android.bat/.ps1` — Windows build/install automation for this port.

## Build System

Shell: Windows PowerShell 5.1. No `&&`; use `;` and `if ($?) { ... }` for chaining. Invoke `.bat` files with `.\` or the call operator.

Target: Android NDK 28, ABI `arm64-v8a`. The translated shard archive `libmkw_base_shared.a` is compiled separately and consumed by the APK build.

### Workflow

1. **Bootstrap (fresh clone)** — `.\android-bootstrap.bat` auto-detects the SDK/NDK/CMake/Ninja, writes `android\local.properties`, stages `Assets\main.dol` + `StaticR.rel` (own RMCP01 dump), and runs the translator if `generated\build_shards\shards.cmake` is missing. `-Install` also pushes the APK to a connected phone.
2. **Shards** — `.\build-shards.bat` compiles the C++ shards with CMake+Ninja and copies `libmkw_base_shared.a` into `jniLibs/arm64-v8a/`. It skips compilation if the archive already exists.
3. **App** — `.\build-app.bat` builds a **debug** or **release** APK. It triggers `build-shards.bat` automatically if the prebuilt archive is missing; otherwise it skips straight to Gradle (`gradlew.bat assembleDebug` / `assembleRelease`).
4. **Install** — `.\install-app.bat` installs a built APK over ADB.

`build-shards.bat` resolves the NDK/CMake/Ninja from `ANDROID_HOME`/`ANDROID_NDK_HOME` env vars, `local.properties`, and well-known SDK install locations — never hardcoded. `build.gradle.kts` only enables the `ccache` compiler launcher when ccache is on PATH.

### Output APK Paths

* Debug: `android\app\build\outputs\apk\debug\app-debug.apk`
* Release: `android\app\build\outputs\apk\release\app-release.apk`

### Gradle

Gradle build runs from `android\` using `gradlew.bat` (`assembleDebug`, `assembleRelease`). Android toolchain (NDK, CMake) is resolved via `local.properties` / the SDK and NDK paths in `build-shards.bat`.

### Fresh-Clone Special Case

`generated\` (translated shard sources, ~405 MB) and `jniLibs\...\libmkw_base_shared.a` (prebuilt archive, ~1.8 GB) are **not tracked**. On a fresh clone you must run the translator once before any build: `android-bootstrap.bat` does this automatically. The translator requires the .NET 8 SDK and the game's own `main.dol` + `StaticR.rel` in `Assets`. See **"Compiling the Game Yourself"** in `README.md` for the full user-facing guide.

## Conventions

* **Android code**: lives under `com.wiicompiled.mkw`; match existing patterns for the disc importer, sensor/touch input, and fullscreen surface. SDL glue stays under `org.libsdl`.
* **Native code**: JNI bridge in `app/src/main/cpp`; shard/CMake sources under `cmake/shards`.
* **License/attribution**: never edit `THIRD_PARTY_NOTICES.md` casually; keep all licensing and attribution records in that file, not in this file or README.

## Important Notes

* Do **not** commit secrets, signed APKs, or game assets.
* This project contains no Nintendo proprietary code or retail assets; do not add any.
* When in doubt about the build, prefer invoking `build-app.bat` / `android-bootstrap.bat` over calling Gradle directly so the shard dependency is handled automatically.
