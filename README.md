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
- `ledger/AGENTS.md` — full third-party license ledger and mandatory attributions.
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

Prebuilt APKs are not shipped yet (work in progress). To build yourself:

You need the Android SDK, Android NDK 28+ (`28.2.13676358` used during development), Android
Studio or a command line with `JAVA_HOME` configured, and the game.

```powershell
# From the android/ directory, with the SDK installed
.\gradlew.bat assembleDebug
```

Then install and run on your connected device:

```powershell
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.wiicompiled.mkw/.MainActivity
```

In the app:

1. Tap **select disc…** and pick your `RMCP01.wbfs` or `.iso`.
2. Wait for the import/verification to finish (game data is extracted into the app's private
   storage).
3. Tap **start**. The game loads and renders through Vulkan; steer with the screen or by tilting
   the device.

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

**Why is there an AGENTS.md?**
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
notices — is in **[`AGENTS.md`](AGENTS.md)**.

## License

This project is licensed under the **GNU General Public License v3.0** (GPL-3.0), same as
upstream WiiCompiled. See [`LICENSE`](LICENSE).

Not affiliated with, endorsed by, or associated with Nintendo. Mario Kart Wii is a trademark of
Nintendo. No Nintendo intellectual property is contained in, distributed with, or obtainable
through this project.