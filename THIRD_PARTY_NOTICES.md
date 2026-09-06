# Third-Party Licenses and Attributions

This document records the licenses, copyrights, notices, and attribution requirements for all components involved in the **WiiCompiled** and **KartPad** codebases, as well as the planned **Android port**.

---

## 1. Upstream Projects

### WiiCompiled
* **Author / Maintainer:** patchzyy and contributors
* **Repository:** [https://github.com/patchzyy/Wiicompiled](https://github.com/patchzyy/Wiicompiled)
* **License:** GNU General Public License v3.0 (GPL-3.0)
* **License Text:** See upstream LICENSE
* **Description:** Ahead-of-time PowerPC static recompilation pipeline and runtime environment for Mario Kart Wii.

### KartPad
* **Author / Maintainer:** chrissotraidis and contributors
* **Repository:** [https://github.com/chrissotraidis/kartpad](https://github.com/chrissotraidis/kartpad)
* **License:** Source-available community software / GPL-3.0 aggregate; contains original touch controls, DiscIO integration, and Apple platform bindings.
* **Notices:** RIGHTS_AND_LICENSES.md, THIRD_PARTY_NOTICES.md
* **Description:** Native Apple Silicon (iOS, iPadOS, macOS, tvOS) port using WiiCompiled runtime, Dawn (Metal), and touch/motion overlay architecture.

---

## 2. Core Dependencies & Libraries

| Component | License | Copyright / Origin | Usage / Role |
|---|---|---|---|
| **Aurora** | MIT | Copyright (c) 2022 Luke Street (encounter/aurora) | GameCube/Wii hardware abstraction (GX, VI, PAD, CARD, SI) |
| **Dawn (WebGPU)** | BSD-3-Clause | Google LLC / Chromium authors | Cross-platform GPU backend (Metal, Vulkan, D3D12) |
| **Dolphin Emulator (DiscIO & HLE)** | GPL-2.0-or-later | Dolphin Emulator Project | DiscIO (WBFS/ISO reading & extraction), WiiConnect24 bootstrap data, Riivolution parser, DSP coef ROM |
| **SDL 3** | zlib | Simple DirectMedia Layer (Sam Lantinga and contributors) | Windowing, input routing, audio playback, native platform abstraction |
| **SunPad** | GPL-3.0 | Copyright (c) SunPad contributors | On-screen touch layout, touch overlay controls, button bindings |
| **libco** | ISC (valgrind.h: BSD-style) | Copyright byuu and higan team | Symmetric stackful coroutines for guest OSThread scheduling |
| **pugixml** | MIT | Copyright (c) 2006-2025 Arseny Kapoulkine | XML parser for Riivolution patch handling |
| **Crypto++** | Boost Software License 1.0 / Public Domain | Copyright (c) 1995-2019 Wei Dai and contributors | SHA-1, ECC sect233r1 cryptographic functions for Wii ES tickets/signatures |
| **toml11** | MIT | Copyright (c) 2017 Toru Niina | TOML configuration parser |
| **YamlDotNet** | MIT | Copyright (c) Antoine Aubry and contributors | YAML deserialization for the translator CLI |
| **xxHash** | BSD-2-Clause | Copyright (c) Yann Collet (Cyan4973) | Fast hashing algorithm for caches and textures |
| **FreeType** | FreeType License (FTL) | Copyright (c) 2026 The FreeType Project | Font rendering (credit required under FTL terms) |
| **zlib / zlib-ng** | zlib | Copyright (c) Jean-loup Gailly and Mark Adler | Compression/decompression for disc and archive formats |
| **Zstandard (zstd)** | BSD-3-Clause | Copyright (c) Meta Platforms, Inc. and affiliates | Pipeline and texture cache compression |
| **libpng** | PNG Reference Library License v2 | PNG Development Group | PNG decoding for textures and UI assets |
| **Dear ImGui** | MIT | Copyright (c) 2014-2025 Omar Cornut | In-game debug and settings overlay UI |
| **Tracy Profiler** | BSD-3-Clause | Copyright (c) 2017-2024 Bartosz Taudul | Performance profiling and frame-time telemetry |
| **SQLite** | Public Domain | D. Richard Hipp | Database storage for shader and pipeline cache metadata |
| **nod / nodtool** | MIT or Apache-2.0 | Copyright (c) Luke Street (encounter/nod) | Disc format parsing and optical disc image extraction |

---

## 3. Mandatory License Notices

### FreeType Credit Notice (FTL)
> Portions of this software are copyright (c) 2026 The FreeType Project (www.freetype.org). All rights reserved.

### Dolphin Emulator Attribution (GPL-2.0-or-later)
> Incorporates DiscIO, DSP coefficient ROM (dsp_coef.bin), WiiConnect24 files, and Riivolution parsing logic derived from the Dolphin Emulator project (https://github.com/dolphin-emu/dolphin), licensed under GNU General Public License v2.0 or later.

### Nintendo Intellectual Property Disclaimer
> Neither WiiCompiled, KartPad, nor this Android port contains Nintendo proprietary code, disc images, models, textures, music, sound effects, or retail game assets. Mario Kart Wii, its characters, logos, and game assets remain the exclusive intellectual property of Nintendo Co., Ltd. Users must supply their own legally obtained game disc image (RMCP01 PAL rev 0).
