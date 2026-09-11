# Unimplemented Features & Backlog

This document keeps track of features, toggles, and stubs that were identified during codebase audits and removed from the user-facing launcher UI to keep the interface clean and avoid misleading options. They can be re-enabled once native runtime support is fully implemented.

---

## 1. Online Network Play (Wiimmfi HLE)

- **Previous UI Location**: Category *Features & Network* -> Switch `Online Network (Wiimmfi)` (`switchNetworkEnabled`)
- **Config Key**: `[network] enabled = true/false`
- **Current Status**: **Stubbed / Incomplete in Native Code**
- **Details**:
  - In `runtime/src/network/network_core.cpp` and `network_socket.cpp`, basic socket initialization exists, but high-level emulation (HLE) of the Nintendo Wi-Fi Connection / Wiimmfi authentication handshakes, matchmaking protocols, and SSL certificate verification are not complete for the Android ARM64 target.
  - Enabling this toggle caused the runtime to attempt network operations that immediately fail with `NetFail` logs or silent guest network errors.
- **Requirements to Re-enable**:
  - Implement full SSL / DWC (Dynamic Wireless Communications) packet serialization.
  - Test connection stability to Wiimmfi on Android.
  - Re-add the switch to `activity_main.xml` and wire it to `binding.switchNetworkEnabled`.

---

## 2. Discord Rich Presence

- **Config Key**: `[discord] enabled = false`
- **Current Status**: **Disabled on Android**
- **Details**:
  - Upstream desktop WiiCompiled uses `discord-rpc` / `discord-game-sdk` to broadcast status to Discord desktop clients.
  - Discord IPC sockets / named pipes are not standardly supported across Android sandboxes without specialized Discord Android IPC intents.
  - Hardcoded to `enabled = false` in `Config.toml`.

---

## 3. Automated HD Texture Pack Manager

- **Previous UI Location**: Category *Features & Network* -> Switch `Texture Replacements` (`switchTextureReplacements`)
- **Config Key**: `[video] texture_replacements = true/false`
- **Current Status**: **Partially Functional (Manual Placement Only)**
- **Details**:
  - Aurora supports texture replacement loading if custom textures are placed at `UserData/Load/Textures/RMCP01/`.
  - However, without an in-app pack importer, folder picker, or unpacker, end users had no intuitive way to stage packs, making the switch appear non-functional.
- **Next Steps**:
  - Implement a dedicated "Import Texture Pack (.zip)" button with a file picker to unzip directly into the target directory before exposing the toggle.

---

## 4. Crash Memory Dumps (`mem1.bin` & `mem2`)

- **Current Status**: **Pruning Implemented**
- **Details**:
  - On unhandled guest crashes, `SystemBridge::WriteGuestMemorySnapshot()` dumps uncompressed guest memory (`mem1.bin` is 24 MB, `mem2` is 64 MB = ~88 MB per crash).
  - This rapidly fills internal mobile storage.
  - The launcher now automatically prunes all but the latest 3 session folders and removes old dump files on startup and resume.
