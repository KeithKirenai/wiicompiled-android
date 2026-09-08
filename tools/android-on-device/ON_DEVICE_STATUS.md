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
   - Native Clang / Ninja compiling C++20 targets with `-O3 -DNDEBUG -fPIC`.
   - Generates `libmkw_base_shared.a` with strictly the 72 shards (excluding `data_sections_init.cpp` and `guest_symbol_table.cpp` which link directly in Android Studio/Gradle).

---

## On-Device Setup & Build Guide (Termux + PRoot)

### 1. Termux Initial Setup & Dependencies
Install Termux from [F-Droid](https://f-droid.org/en/packages/com.termux/) (avoid Play Store builds). Open Termux and run:

```bash
# Storage access & base tooling
termux-setup-storage
pkg update -y
pkg install -y git clang cmake ninja proot-distro
```

### 2. Set Up Debian PRoot for .NET 8 CoreCLR

Install and enter the Debian rootfs:

```bash
proot-distro install debian
proot-distro login debian
```

Inside Debian (`root@localhost:~#`):

```bash
apt update && apt install -y wget curl git build-essential
wget https://packages.microsoft.com/config/debian/12/packages-microsoft-prod.deb -O packages-microsoft-prod.deb
dpkg -i packages-microsoft-prod.deb
rm packages-microsoft-prod.deb
apt update && apt install -y dotnet-sdk-8.0
exit
```

### 3. Clone Repository & Build `Translator.Cli`

In Termux (`~`):

```bash
cd ~
git clone https://github.com/KeithKirenai/wiicompiled-android.git
cd ~/wiicompiled-android

# Publish self-contained ARM64 Translator binary via Debian
proot-distro run debian -- bash -c "
  cd /data/data/com.termux/files/home/wiicompiled-android/translator/src/Translator.Cli && \
  dotnet publish -c Release -r linux-arm64 --self-contained true -o /data/data/com.termux/files/home/Translator.Cli_dir
"

cp ~/Translator.Cli_dir/Translator.Cli ~/Translator.Cli
chmod +x ~/Translator.Cli
```

### 4. Place Extracted Disc Assets

Copy your PAL `RMCP01` game dump assets to the repository root:

```bash
cd ~/wiicompiled-android
mkdir -p Assets

# Example: copy from phone's Download folder
cp /sdcard/Download/main.dol Assets/
cp /sdcard/Download/StaticR.rel Assets/
```

Verify that `Assets/main.dol`, `Assets/StaticR.rel`, and `projects/mkwii/MAP.txt` are present.

### 5. Run the On-Device Build Script

```bash
cd ~/wiicompiled-android/tools/android-on-device
chmod +x build-mkwii.sh
./build-mkwii.sh
```

### 6. Stage Compiled Archive for APK Packaging

Once built, copy the static archive to `jniLibs`:

```bash
mkdir -p ~/wiicompiled-android/android/app/src/main/jniLibs/arm64-v8a/
cp ~/wiicompiled-android/dist/libmkw_base_shared.a ~/wiicompiled-android/android/app/src/main/jniLibs/arm64-v8a/
```
