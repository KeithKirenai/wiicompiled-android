#!/usr/bin/env bash
# Packages Launcher/WiiCompiled.Setup.Linux as a self-contained AppImage: a single file Wheel
# Wizard (or anyone else) can fetch and execute with no git clone, no `dotnet` install, and no
# `dolphin-tool` package required at all. The installer and translator are published as
# self-contained binaries, and `nodtool` (a prebuilt MIT/Apache-2.0 CLI from encounter/nod, see
# NodToolProvider.cs) is downloaded and bundled too - AppRun passes --translator-bin and
# --disc-tool-bin so local-build.sh/DiscTool.cs skip their from-source/download fallbacks entirely.
# It still shells out to system clang/cmake/ninja - no C/C++ toolchain is bundled, matching
# Launcher/local-build.sh's own remaining prerequisites.
#
# An AppImage mounts read-only, but local-build.sh writes generated/, native-build/, Assets/, etc.
# into the workspace it's given. So AppRun (written below) copies the bundled workspace snapshot
# out to a writable cache directory on first run, and only ever re-syncs the bundled directories
# (runtime/, aurora-main/, projects/, local-build.sh) on a later run whose bundled version changed
# - generated/native-build/Assets/PulsarPacks live only in that writable cache and are never
# touched by the sync, so local-build.sh's own incremental caching survives across runs and across
# AppImage updates. translator/ isn't part of this snapshot at all: it's published as its own
# self-contained binary (usr/bin/translator-cli) below and never needs a writable copy.
set -euo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
workspace=$(cd "$script_dir/.." && pwd)

output_dir="$workspace/Launcher/dist"
appimagetool_override=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --output-dir) output_dir=$2; shift 2 ;;
        --appimagetool) appimagetool_override=$2; shift 2 ;;
        -h|--help)
            echo "Usage: build-appimage.sh [--output-dir DIR] [--appimagetool PATH]"
            exit 0
            ;;
        *) echo "build-appimage.sh: unknown argument: $1" >&2; exit 1 ;;
    esac
done

appdir="$workspace/Launcher/artifacts/appimage-build/AppDir"
rm -rf "$appdir"
mkdir -p "$appdir/usr/bin" "$appdir/workspace/Launcher"

echo "Publishing the installer (self-contained linux-x64)..."
publish_tmp="$workspace/Launcher/artifacts/appimage-build/publish"
rm -rf "$publish_tmp"
dotnet publish "$workspace/Launcher/WiiCompiled.Setup.Linux" -c Release -r linux-x64 \
    --self-contained -p:PublishSingleFile=true -p:EnableCompressionInSingleFile=true \
    -o "$publish_tmp"
cp "$publish_tmp/WiiCompiled.Setup.Linux" "$appdir/usr/bin/wiicompiled-setup"
chmod +x "$appdir/usr/bin/wiicompiled-setup"

# Published as a self-contained binary too, so an AppImage user never needs a `dotnet` SDK on
# PATH at all - local-build.sh is told about it via --translator-bin and skips its own
# dotnet-build-from-source step entirely (see local-build.sh's translator resolution branch).
echo "Publishing the translator (self-contained linux-x64)..."
translator_publish_tmp="$workspace/Launcher/artifacts/appimage-build/publish-translator"
rm -rf "$translator_publish_tmp"
dotnet publish "$workspace/translator/src/Translator.Cli" -c Release -r linux-x64 \
    --self-contained -p:PublishSingleFile=true -p:EnableCompressionInSingleFile=true \
    -o "$translator_publish_tmp"
cp "$translator_publish_tmp/Translator.Cli" "$appdir/usr/bin/translator-cli"
chmod +x "$appdir/usr/bin/translator-cli"

# Resolved via the shared WiiCompiled.NodTool.Cli helper (also used by Build-Installer.ps1 on
# Windows) rather than a second curl/version-pin copy here: it downloads and caches the same way
# NodToolProvider.cs always does (Launcher/artifacts/nodtool), so there is exactly one place that
# knows the nodtool version/URL/platform-asset mapping.
echo "Resolving nodtool..."
nodtool_path=$(dotnet run --project "$workspace/Launcher/WiiCompiled.NodTool.Cli" -c Release -- \
    --workspace "$workspace" | tail -n1)
cp "$nodtool_path" "$appdir/usr/bin/nodtool"
chmod +x "$appdir/usr/bin/nodtool"

echo "Staging the bundled workspace snapshot..."
for dir in runtime aurora-main projects; do
    cp -r "$workspace/$dir" "$appdir/workspace/$dir"
done
# Mirrors Build-Installer.ps1's own staging exclusions exactly: aurora-main/extern/CMakeLists.txt
# is the real FetchContent driver and must ship, but any already-fetched dependency *subdirectory*
# a developer's local checkout accumulated under extern/ is stale/large build output, not a
# release input - only directories inside extern/ are stripped, never the file itself. runtime/build
# is a plain developer build directory.
find "$appdir/workspace/aurora-main/extern" -mindepth 1 -maxdepth 1 -type d -exec rm -rf {} +
rm -rf "$appdir/workspace/runtime/build"
cp "$workspace/Launcher/local-build.sh" "$appdir/workspace/Launcher/local-build.sh"

if git -C "$workspace" rev-parse HEAD >/dev/null 2>&1; then
    git -C "$workspace" rev-parse HEAD > "$appdir/workspace/.bundle-version"
else
    date -u +%s > "$appdir/workspace/.bundle-version"
fi

echo "Writing AppRun..."
cat > "$appdir/AppRun" <<'APPRUN'
#!/bin/bash
set -euo pipefail
HERE="$(dirname "$(readlink -f "$0")")"
CACHE="${XDG_DATA_HOME:-$HOME/.local/share}/WiiCompiled/workspace"
if [ ! -f "$CACHE/.bundle-version" ] || \
   [ "$(cat "$HERE/workspace/.bundle-version")" != "$(cat "$CACHE/.bundle-version")" ]; then
    mkdir -p "$CACHE/Launcher"
    for dir in runtime aurora-main projects; do
        rm -rf "$CACHE/$dir"
        cp -r "$HERE/workspace/$dir" "$CACHE/$dir"
    done
    cp "$HERE/workspace/Launcher/local-build.sh" "$CACHE/Launcher/local-build.sh"
    cp "$HERE/workspace/.bundle-version" "$CACHE/.bundle-version"
fi
exec "$HERE/usr/bin/wiicompiled-setup" --workspace "$CACHE" \
    --translator-bin "$HERE/usr/bin/translator-cli" \
    --disc-tool-bin "$HERE/usr/bin/nodtool" "$@"
APPRUN
chmod +x "$appdir/AppRun"

echo "Writing desktop entry and icon..."
cat > "$appdir/wiicompiled-setup.desktop" <<'DESKTOP'
[Desktop Entry]
Type=Application
Name=WiiCompiled Setup
Comment=Translate, compile, and launch Mario Kart Wii natively on Linux
Exec=AppRun
Icon=wiicompiled-setup
Categories=Game;
Terminal=true
DESKTOP

# No WiiCompiled logo/icon asset exists anywhere in this repo yet. appimagetool refuses to package
# without one, so this is a minimal solid-color placeholder - a one-line swap for real branding
# later (just replace this generated file with a real wiicompiled-setup.png before packaging).
python3 - "$appdir/wiicompiled-setup.png" <<'PY'
import struct
import sys
import zlib

path = sys.argv[1]


def chunk(tag: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data))


width = height = 256
row = b"\x00" + bytes([0x3A, 0x5F, 0x8F, 0xFF]) * width  # filter byte + opaque blue-grey pixels
raw = row * height
ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
idat = zlib.compress(raw, 9)

with open(path, "wb") as handle:
    handle.write(b"\x89PNG\r\n\x1a\n")
    handle.write(chunk(b"IHDR", ihdr))
    handle.write(chunk(b"IDAT", idat))
    handle.write(chunk(b"IEND", b""))
PY

echo "Resolving appimagetool..."
appimagetool="$appimagetool_override"
if [[ -z "$appimagetool" ]]; then
    appimagetool="$workspace/Launcher/artifacts/appimagetool"
    if [[ ! -x "$appimagetool" ]]; then
        echo "Downloading appimagetool..."
        mkdir -p "$(dirname "$appimagetool")"
        curl -fsSL "https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage" \
            -o "$appimagetool"
        chmod +x "$appimagetool"
    fi
fi

mkdir -p "$output_dir"
echo "Packaging..."
# appimagetool detects the target architecture from the first ELF executable it finds in the
# AppDir; AppRun here is a shell script, not ELF, so ARCH must be set explicitly.
ARCH=x86_64 "$appimagetool" "$appdir" "$output_dir/WiiCompiled-Setup-x86_64.AppImage"
echo "Built: $output_dir/WiiCompiled-Setup-x86_64.AppImage"
