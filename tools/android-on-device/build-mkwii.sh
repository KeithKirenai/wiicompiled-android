#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK_DIR="$BASE_DIR"
CLI_PATH="/data/data/com.termux/files/home/Translator.Cli"
MANIFEST="$WORK_DIR/projects/mkwii/recomp.yml"
METADATA_OUT="$WORK_DIR/generated/base_translation_output.json"
BUILD_DIR="$WORK_DIR/build"
OUTPUT_DIR="$WORK_DIR/dist"

echo "[1/5] Verifying workspace prerequisites..."
for file in "$CLI_PATH" "$MANIFEST" "$WORK_DIR/Assets/main.dol" "$WORK_DIR/Assets/StaticR.rel" "$WORK_DIR/projects/mkwii/MAP.txt"; do
    if [ ! -f "$file" ]; then
        echo "Error: Missing required file: $file" >&2
        exit 1
    fi
done

mkdir -p "$WORK_DIR/generated" "$OUTPUT_DIR"

echo "[2/5] Running recursive translation (PowerPC -> C++)..."
proot-distro run debian --work-dir "$WORK_DIR" -- env \
  DOTNET_gcServer=0 \
  DOTNET_GCHeapHardLimit=0x80000000 \
  DOTNET_GCHeapHardLimitPercent=50 \
  DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1 \
  "$CLI_PATH" translate-recursive 0x800060A4 \
    --project "$MANIFEST" \
    --output-metadata "$METADATA_OUT" \
    --threads 4

echo "[3/5] Generating data section initializers..."
proot-distro run debian --work-dir "$WORK_DIR" -- env \
  DOTNET_gcServer=0 \
  DOTNET_GCHeapHardLimit=0x80000000 \
  DOTNET_GCHeapHardLimitPercent=50 \
  DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1 \
  "$CLI_PATH" generate-data-init \
    --project "$MANIFEST"

echo "[4/5] Emitting build shards..."
proot-distro run debian --work-dir "$WORK_DIR" -- env \
  DOTNET_gcServer=0 \
  DOTNET_GCHeapHardLimit=0x80000000 \
  DOTNET_GCHeapHardLimitPercent=50 \
  DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1 \
  "$CLI_PATH" emit-build-shards \
    --project "$MANIFEST"

echo "[5/5] Compiling and archiving libmkw_base_shared.a via native Clang/Ninja..."
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

cmake -G Ninja -DCMAKE_BUILD_TYPE=Release ..
ninja -j4

cp "$BUILD_DIR/libmkw_base_shared.a" "$OUTPUT_DIR/"

echo "--------------------------------------------------------"
echo "Build successful!"
ls -lh "$OUTPUT_DIR/libmkw_base_shared.a"
echo "Archive staged at: $OUTPUT_DIR/libmkw_base_shared.a"
echo "--------------------------------------------------------"
