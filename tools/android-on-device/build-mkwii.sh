#!/usr/bin/env bash
# Turnkey on-device build script for Mario Kart Wii recompilation (Termux + PRoot Debian).
# Handles: Termux deps -> Debian + .NET 8 -> repo clone -> CLI publish -> translate ->
# shards -> compile 72-shard libmkw_base_shared.a -> stage into jniLibs.
# Asset stage (main.dol, StaticR.rel) is MANUAL: copy them into Assets/ yourself
# (e.g. from /sdcard/Download/). This script never attempts ISO/WBFS extraction.

set -euo pipefail

REPO_DIR="$HOME/wiicompiled-android"
CLI_DIR="$HOME/Translator.Cli_dir"
CLI_BIN="$HOME/Translator.Cli"
ASSETS_DIR="$REPO_DIR/Assets"
PROOT_DISTRO_NAME="debian"

# ---------------------------------------------------------------------------
# 1. Termux deps (idempotent — pkg will no-op on already-installed packages)
# ---------------------------------------------------------------------------
echo "=== [1/6] Termux dependencies ==="
termux-setup-storage || true
pkg update -y
pkg install -y git clang cmake ninja proot-distro llvm-tools wget

# ---------------------------------------------------------------------------
# 2. Debian PRoot + .NET 8 SDK (idempotent)
# ---------------------------------------------------------------------------
echo "=== [2/6] Debian PRoot + .NET 8 SDK ==="
if ! proot-distro list 2>/dev/null | grep -q "${PROOT_DISTRO_NAME} (installed)"; then
    echo "Installing ${PROOT_DISTRO_NAME} rootfs..."
    proot-distro install "${PROOT_DISTRO_NAME}"
fi

proot-distro run "${PROOT_DISTRO_NAME}" -- bash -c '
    apt update && apt install -y wget curl git build-essential libicu-dev
    if ! command -v dotnet &> /dev/null; then
        wget -q https://packages.microsoft.com/config/debian/12/packages-microsoft-prod.deb \
            -O /tmp/packages-microsoft-prod.deb
        dpkg -i /tmp/packages-microsoft-prod.deb
        rm -f /tmp/packages-microsoft-prod.deb
        apt update && apt install -y dotnet-sdk-8.0
    fi
'

# ---------------------------------------------------------------------------
# 3. Repository + self-contained Translator.Cli (idempotent)
# ---------------------------------------------------------------------------
echo "=== [3/6] Repository + Translator.Cli ==="
if [ ! -d "$REPO_DIR" ]; then
    git clone https://github.com/KeithKirenai/wiicompiled-android.git "$REPO_DIR"
fi

if [ ! -f "$CLI_BIN" ]; then
    echo "Publishing Translator.Cli for linux-arm64..."
    proot-distro run "${PROOT_DISTRO_NAME}" -- bash -c "
        cd \"$REPO_DIR/translator/src/Translator.Cli\" && \
        dotnet publish -c Release -r linux-arm64 --self-contained true -o \"$CLI_DIR\"
    "
    cp "$CLI_DIR/Translator.Cli" "$CLI_BIN"
    chmod +x "$CLI_BIN"
fi

# ---------------------------------------------------------------------------
# 4. Asset prerequisites (MANUAL — no ISO/WBFS extraction on device)
# ---------------------------------------------------------------------------
echo "=== [4/6] Asset prerequisites ==="
mkdir -p "$ASSETS_DIR"

# If Assets/ is empty, try copying from the phone's Downloads folder (convenience,
# not extraction). If that also fails, bail with clear instructions.
if [ ! -f "$ASSETS_DIR/main.dol" ] || [ ! -f "$ASSETS_DIR/StaticR.rel" ]; then
    if [ -f "/sdcard/Download/main.dol" ] && [ -f "/sdcard/Download/StaticR.rel" ]; then
        echo "Copying pre-extracted main.dol + StaticR.rel from /sdcard/Download/..."
        cp "/sdcard/Download/main.dol" "$ASSETS_DIR/"
        cp "/sdcard/Download/StaticR.rel" "$ASSETS_DIR/"
    fi
fi

for f in "$ASSETS_DIR/main.dol" "$ASSETS_DIR/StaticR.rel" \
         "$REPO_DIR/projects/mkwii/MAP.txt" "$REPO_DIR/projects/mkwii/recomp.yml"; do
    if [ ! -f "$f" ]; then
        echo "ERROR: Missing required file: $f" >&2
        echo "" >&2
        echo "Asset setup (do this once, manually):" >&2
        echo "  1. Obtain your own PAL RMCP01 main.dol + StaticR.rel" >&2
        echo "     (extract from your disc with Dolphin Android / cleanrip on desktop)." >&2
        echo "  2. Copy them into ~/wiicompiled-android/Assets/:" >&2
        echo "       mkdir -p ~/wiicompiled-android/Assets" >&2
        echo "       cp /path/to/main.dol ~/wiicompiled-android/Assets/" >&2
        echo "       cp /path/to/StaticR.rel ~/wiicompiled-android/Assets/" >&2
        echo "  OR copy from phone Downloads:" >&2
        echo "       cp /sdcard/Download/main.dol ~/wiicompiled-android/Assets/" >&2
        echo "       cp /sdcard/Download/StaticR.rel ~/wiicompiled-android/Assets/" >&2
        exit 1
    fi
done

# ---------------------------------------------------------------------------
# 5. Translate + shard emission (reruns each invocation — intentionally)
# ---------------------------------------------------------------------------
echo "=== [5/6] Translation + shard emission ==="
cd "$REPO_DIR"
mkdir -p generated build dist

proot-distro run "${PROOT_DISTRO_NAME}" --work-dir "$REPO_DIR" -- env \
    DOTNET_gcServer=0 \
    DOTNET_GCHeapHardLimit=0x80000000 \
    DOTNET_GCHeapHardLimitPercent=50 \
    DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1 \
    "$CLI_BIN" translate-recursive 0x800060A4 \
        --project "$REPO_DIR/projects/mkwii/recomp.yml" \
        --output-metadata "$REPO_DIR/generated/base_translation_output.json" \
        --threads 4

proot-distro run "${PROOT_DISTRO_NAME}" --work-dir "$REPO_DIR" -- env \
    DOTNET_gcServer=0 \
    DOTNET_GCHeapHardLimit=0x80000000 \
    DOTNET_GCHeapHardLimitPercent=50 \
    DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1 \
    "$CLI_BIN" generate-data-init \
        --project "$REPO_DIR/projects/mkwii/recomp.yml"

proot-distro run "${PROOT_DISTRO_NAME}" --work-dir "$REPO_DIR" -- env \
    DOTNET_gcServer=0 \
    DOTNET_GCHeapHardLimit=0x80000000 \
    DOTNET_GCHeapHardLimitPercent=50 \
    DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1 \
    "$CLI_BIN" emit-build-shards \
        --project "$REPO_DIR/projects/mkwii/recomp.yml"

# ---------------------------------------------------------------------------
# 6. Compile 72-shard archive + stage into jniLibs
# ---------------------------------------------------------------------------
echo "=== [6/6] Compile 72-shard archive + stage ==="

# CMakeLists.txt in tools/android-on-device/ is repo-root-anchored, so cmake
# invoked with -S "$BASE_DIR" from $WORK_DIR resolves all includes correctly.
cmake -G Ninja -B build -S tools/android-on-device -DCMAKE_BUILD_TYPE=Release
ninja -C build -j4

SHARD_COUNT=$(llvm-objdump -a build/libmkw_base_shared.a 2>/dev/null \
    | grep -c "\.o):" || echo 0)
echo "[+] Archive object count: $SHARD_COUNT (expected 72)"

if [ "$SHARD_COUNT" -ne 72 ]; then
    echo "WARNING: expected 72 objects, got $SHARD_COUNT" >&2
fi

cp build/libmkw_base_shared.a dist/
mkdir -p "$REPO_DIR/android/app/src/main/jniLibs/arm64-v8a"
cp build/libmkw_base_shared.a "$REPO_DIR/android/app/src/main/jniLibs/arm64-v8a/"

echo "============================================================"
echo "Build finished."
echo "  dist/libmkw_base_shared.a"
echo "  android/app/src/main/jniLibs/arm64-v8a/libmkw_base_shared.a"
echo "============================================================"
