# WiiCompiled — Android Port

This repository is an **Android port of [WiiCompiled](https://github.com/patchzyy/Wiicompiled)**,
the native static-recompilation project that plays **Mario Kart Wii** with no emulator,
interpreter, or JIT in the loop. The port is **AI-assisted**, and both the engine and the
mobile control layer come from two upstream projects; see [Credits & Licenses](#credits--licenses).

> [!NOTE]
> This is a work-in-progress Android port, not the upstream desktop project. It **boots and renders**
> on an Android device, but it is still **experimental and slow** — performance work is ongoing.

> [!IMPORTANT]
> There is no Nintendo code, no assets and no game data anywhere in this project or its releases.
> You need your own legally dumped copy of the **PAL (`RMCP01`)** version of the game. Only your
> own `.wbfs` / `.iso` image is loaded, extraction runs on your device, and nothing is uploaded.

---

## What this is

WiiCompiled compiles the entire PowerPC game to native AArch64 code ahead of time. Nothing
emulates a Wii CPU or GPU at runtime. This fork takes that engine and brings it up on Android:

- **Native AArch64** recompiled game running on `arm64-v8a` devices.
- **Aurora + Dawn (Vulkan)** based rendering onto an Android `SurfaceView`.
- An **Android Gradle app** (`com.wiicompiled.mkw`) with a disc-import and game-launch frontend.
- **On-screen touch controls** (accelerate / drift / item zones) and **accelerometer tilt steering**.
- Cooperative AArch64 coroutines for guest OS thread scheduling (`co_switch_android.S`).

## The changes made (the Android port work)

The upstream engine is desktop Windows/macOS. This fork adds the missing Android platform:

- `android/` — a complete Gradle app using Kotlin for the UI and the NDK for the game runtime:
  - `MainActivity` — Storage Access Framework disc picker; imports and verifies your `.wbfs`/`.iso`
    into the app's private data directory, then enables launch.
  - `GameActivity` — fullscreen immersive surface, multitouch dispatcher, and accelerometer sensor
    feed into the native engine.
  - `app/src/main/cpp/` — JNI bridge (`libmkw_android.so`), Android touch input adapter,
    disc extraction bridge, and the Android render-loop / Vulkan wiring.
- `THIRD_PARTY_NOTICES.md` — full third-party license ledger and mandatory attributions.
- AArch64 fiber switching for guest OSThreads (Android assembly port of the desktop switcher), a
  `memfd` syscall fallback for older Android kernels, and Android-specific input/audio paths
  adapted from the mobile work in the upstream projects.

The game logic, translation pipeline, and rendering core remain exactly the upstream engine —
Android only transports it.

## Status

What works today, verified on a physical Android 15 device (`arm64-v8a`) via adb:

- App installs and launches.
- `RMCP01.wbfs` / `.iso` import flow.
- Game **boots** and renders through Vulkan.
- Touch and tilt input reach the game.

What is left:

- **Performance** — internal-resolution / pipeline tuning so full races run at acceptable speed.
- Complete race flow, audio tuning, and controller/HUD polish.
- The port is tracked in the commit history as staged checkpoints (`docs:`, `feat(android):`).

## Requirements

- 64-bit Android device, **`arm64-v8a`** (Android 9+ / API 28 or newer).
- Vulkan-capable GPU.
- Your own legal, unmodified **PAL `RMCP01`** Mario Kart Wii disc image `.wbfs` or `.iso`.

> [!NOTE]
> Only the clean PAL revision is supported, matching upstream. Other regions and patched
> executables are rejected.

## How to install

Prebuilt APKs are not shipped yet (work in progress). You must build the APK on your own machine.
A fresh clone does **not** include the translated shard sources (`generated/` is gitignored) or the
prebuilt `libmkw_base_shared.a` archive (~1.8 GB, not tracked) — everyone builds these locally, and
the game spec requires your own legally obtained **RMCP01 PAL (PAL rev 0)** disc dump.

> **Supported platform:** Windows (the build scripts are `.bat`/PowerShell). Other OSes need
> equivalent manual steps.

### Step 0 — Required tools

| Tool | Version | Why | Get it |
|---|---|---|---|
| **Git** | latest | Clone the repository | https://git-scm.com |
| **JDK 17** | 17 | Compile the Android/Kotlin code | Eclipse Temurin: https://adoptium.net |
| **Android SDK + Build-Tools** | 35 | Compile and package the APK | Android Studio, or `sdkmanager` (below) |
| **Android NDK** | 28.2.13676358 | Native C++ toolchain for the shards + JNI | `sdkmanager "ndk;28.2.13676358"` |
| **CMake** | 4.1.2 | Native build (pinned by the Gradle project) | `sdkmanager "cmake;4.1.2"` |
| **Ninja** | any recent | Fast native build driver | `sdkmanager` (bundled with cmake) or standalone |
| **.NET 8 SDK** | 8.x | Runs the translator that emits the C++ shards | https://dotnet.microsoft.com/download/dotnet/8.0 |
| **ADB / Platform-Tools** | latest | Install the APK on your phone | `sdkmanager "platform-tools"` |
| **Your game disc dump** | RMCP01 PAL rev 0 | The `main.dol` + `StaticR.rel` whose code gets recompiled | Your own MKW disc (extract with Dolphin/cleanrip) |

Optional but recommended: **ccache** (greatly speeds up repeated native builds; used automatically if
it is on your PATH).

> **Disk space:** translated sources (~405 MB) + prebuilt archive (~1.8 GB) + build outputs can
> exceed **4 GB** during a full build. First-run translation takes several minutes.

### Step 1 — Install the toolchain

**Option A — Android Studio (easiest)**

1. Install [Android Studio](https://developer.android.com/studio).
2. In **SDK Manager → SDK Platforms**, install **Android API 35**.
3. In **SDK Manager → SDK Tools**, install **NDK; 28.2.13676358**, **CMake 4.1.2**, and
   **Android SDK Platform-Tools**.
4. Install a **JDK 17** and the **.NET 8 SDK**.

**Option B — Command line (no Android Studio)**

With the [Android command-line tools](https://developer.android.com/studio#command-line-tools-only)
installed plus a JDK 17 and the .NET 8 SDK:

```bat
set ANDROID_HOME=C:\AndroidSdk
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" "ndk;28.2.13676358" "cmake;4.1.2"
```

Either way, make `adb` reachable (add `%ANDROID_HOME%\platform-tools` to your `PATH`).

### Step 2 — Clone the repository

```bat
git clone https://github.com/KeithKirenai/wiicompiled-android.git
cd wiicompiled-android
```

### Step 3 — Extract your game disc

Extract **`main.dol`** and **`StaticR.rel`** from your own RMCP01 PAL (PAL rev 0) Mario Kart Wii
disc (e.g. with Dolphin or cleanrip). These two files are the **only** game files the build needs.

### Step 4 — Build & install (one-shot)

From the repository root, run the bootstrap:

```powershell
.\android-bootstrap.bat -Install -DiscSource D:\dumps\RMCP01
```

`-DiscSource` is the folder containing `main.dol` + `StaticR.rel`. If you instead place those two
files into `Assets\` yourself, you can omit `-DiscSource`. The bootstrap will:

1. **Detect** your SDK/NDK/CMake/Ninja and print what it found.
2. **Write** `android\local.properties` with your SDK paths.
3. **Stage** your disc files into `Assets`.
4. **Translate** — run the .NET translator to generate the C++ shard sources (`generated\`) and
   `shards.cmake`. *Only runs if these are missing.*
5. **Build shards** — compile all translated C++ into `libmkw_base_shared.a` (~1.8 GB, the long step).
6. **Build the APK** — `gradlew assembleDebug`.
7. **Install** — because you passed `-Install`, push the APK to your connected phone (OEM USB
   debugging must be enabled).

When it finishes, the game is installed. Open it from your app drawer.

### Build & install separately (after first setup)

Once `generated\` and the prebuilt archive exist, the fast path is:

```powershell
.\build-shards.bat     # compile shards (skips if libmkw_base_shared.a already exists)
.\build-app-debug.bat  # -> debug APK   (or .\build-app-release.bat for release)
.\install-app.bat      # choose debug/release and install over ADB
```

Individual bootstrap stages:

```powershell
.\android-bootstrap.bat -Only Detect           # show detected toolchain
.\android-bootstrap.bat -Only Assets           # stage game files
.\android-bootstrap.bat -Only Translate        # (add -ForceTranslate to re-translate)
.\android-bootstrap.bat -Only Shards           # compile the C++ shards
.\android-bootstrap.bat -Only App -Release     # build a release APK
.\android-bootstrap.bat -Install               # push the built APK to device
```

**Outputs**

| Artifact | Path |
|---|---|
| Debug APK | `android\app\build\outputs\apk\debug\app-debug.apk` |
| Release APK | `android\app\build\outputs\apk\release\app-release.apk` |
| Prebuilt shard archive | `android\app\src\main\jniLibs\arm64-v8a\libmkw_base_shared.a` |
| Generated shard sources | `generated\build_shards\` |

### In the app

1. Tap **select disc…** and pick your `RMCP01.wbfs` or `.iso`.
2. Wait for the import/verification to finish (game data is extracted into the app's private
   storage).
3. Tap **start**. The game loads and renders through Vulkan; steer with the screen or by tilting
   the device.

### Troubleshooting

* **`Android NDK not found`** — `build-shards.bat` can't locate the NDK. Install
  `ndk;28.2.13676358` and re-run the bootstrap's Detect step, or set `ANDROID_NDK_HOME`.
* **`.NET 8 SDK required`** — the translator needs .NET 8 (only on a fresh clone's first build).
* **`Game disc files are required`** — put `main.dol` + `StaticR.rel` in `Assets\` or pass
  `-DiscSource`.
* **`ccache` errors** — it is optional and auto-detected; without it the build simply skips it.
* **First build is slow** — compiling ~29,000 translated functions into a 1.8 GB static archive is
  the bottleneck. Subsequent builds are near-instant because the archive is reused.

> [!CAUTION]
> Only build from this repository. Anyone sharing an installer through Discord or a random
> download site should not be trusted — you would have no idea what it contains.

## FAQ

**Is this an emulator?**
No. Same as upstream: the game is compiled to native code before you press play.

**Do you provide the game?**
No. Don't ask. Nothing in this repo or any build contains Nintendo code or assets.

**Why is it slow right now?**
Because it's an early Android bring-up. Rendering and internal resolution are not tuned for mobile
GPUs yet. This is the main thing being worked on.

**Why is there a THIRD_PARTY_NOTICES.md?**
It is the license ledger for this port. It records every third-party component, its license, its
copyright holder, and the mandatory attribution text (for example the FreeType notice and the
Dolphin DiscIO attribution). If you ship this project, it must stay intact.

## Credits & Licenses

This port exists only because of two upstream projects, both fully attributed here:

- **WiiCompiled** — [patchzyy/Wiicompiled](https://github.com/patchzyy/Wiicompiled)
  - The entire static-recompilation engine: translator, runtime, Aurora/Dawn rendering, audio,
    and disc loading. **GPL-3.0.**
- **KartPad** — [chrissotraidis/kartpad](https://github.com/chrissotraidis/kartpad)
  - The mobile work this port adapts: touch HUD/control architecture, motion steering, DiscIO
    integration, and Apple platform bindings that informed the Android platform layer.
  - Source-available community software / GPL-3.0 aggregate.

Plus the same core dependencies the upstream runtime ships and the upstream
[README](https://github.com/patchzyy/Wiicompiled) already credits: **aurora** (MIT),
**Dawn** (BSD-3), **Dolphin Emulator** (GPL-2.0-or-later, DiscIO + WiiConnect24 data +
DSP coef ROM), **SDL**, **Retro Rewind**, and the static-recompilation community.

The complete ledger — every component, its license, its copyright holder, and the required
notices — is in **[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)**.

## License

This project is licensed under the **GNU General Public License v3.0** (GPL-3.0), same as
upstream WiiCompiled. See [`LICENSE`](LICENSE).

Not affiliated with, endorsed by, or associated with Nintendo. Mario Kart Wii is a trademark of
Nintendo. No Nintendo intellectual property is contained in, distributed with, or obtainable
through this project.