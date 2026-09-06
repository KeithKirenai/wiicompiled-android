<#
.SYNOPSIS
    WiiCompiled Android - one-shot clone-to-phone bootstrap.

.DESCRIPTION
    Detects the Android SDK / NDK / CMake / Ninja / .NET toolchain, writes
    local.properties, validates/stages the MKW disc files, runs the translator
    to regenerate the shard sources if they are missing, then builds the shard
    archive, builds the APK, and (optionally) installs it over ADB.

    Run from the repository root. Safe to re-run: completed steps are skipped.

.PARAMETER DiscSource
    Directory containing the required game disc files (main.dol, StaticR.rel).
    If omitted and the files are missing from Wiicompiled\Assets, you will be
    prompted for a location.

.PARAMETER SkipEnter
    Non-interactive mode. Fail with a clear error instead of prompting when a
    required input (disc files / toolchain) is missing.

.PARAMETER Only
    Only run a subset of the pipeline: Detect, LocalProperties, Assets,
    Translate, Shards, App, Install. Default runs the whole pipeline.

.PARAMETER Release
    Build a release APK instead of a debug APK.

.PARAMETER Install
    Install the built APK over ADB after building.

.PARAMETER ForceTranslate
    Re-run the translator even if generated shard sources already exist.

.PARAMETER SdkRoot
    Override Android SDK root detection.

.PARAMETER NdkRoot
    Override Android NDK root detection.

.EXAMPLE
    .\android-bootstrap.ps1 -Install
    .\android-bootstrap.ps1 -Release -Install -DiscSource D:\dumps\RMCP01
    .\android-bootstrap.ps1 -Only Translate
#>
[CmdletBinding()]
param(
    [string]$DiscSource = "",
    [switch]$SkipEnter,
    [ValidateSet("Detect", "LocalProperties", "Assets", "Translate", "Shards", "App", "Install")]
    [string]$Only = "",
    [switch]$Release,
    [switch]$Install,
    [switch]$ForceTranslate,
    [string]$SdkRoot = "",
    [string]$NdkRoot = ""
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$script:SdkRootDetected = $null
$script:NdkDetected = $null
$script:CmakeDetected = $null
$script:NinjaDetected = $null

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Mkw = $Root
$AndroidDir = Join-Path $Root "android"
$AssetsDir = Join-Path $Root "Assets"
$LocalProps = Join-Path $AndroidDir "local.properties"
$Generated = Join-Path $Root "generated"
$ShardsCmake = Join-Path $Generated "build_shards\shards.cmake"
$ShardArchive = Join-Path $AndroidDir "app\src\main\jniLibs\arm64-v8a\libmkw_base_shared.a"
$Manifest = Join-Path $Root "projects\mkwii\recomp.yml"
$TranslatorCsproj = Join-Path $Root "translator\src\Translator.Cli\Translator.Cli.csproj"
$TranslatorDll = Join-Path $Root "translator\src\Translator.Cli\bin\Release\net8.0\Translator.Cli.dll"

$GameFiles = @("main.dol", "StaticR.rel")

function Write-Step($Title, $Sub = "") {
    Write-Host ""
    Write-Host "== $Title ==" -ForegroundColor Cyan
    if ($Sub) { Write-Host $Sub -ForegroundColor DarkGray }
}

function Fail($Message, [int]$Code = 1) {
    Write-Host "[ERROR] $Message" -ForegroundColor Red
    if ($SkipEnter) { exit $Code }
    $reply = Read-Host "Press Enter to continue..."
    exit $Code
}

function Find-AndroidSdk {
    $candidates = @($SdkRoot.trim(), $env:ANDROID_HOME, $env:ANDROID_SDK_ROOT,
        (Join-Path $env:LOCALAPPDATA "Android\Sdk"),
        (Join-Path $env:USERPROFILE "AppData\Local\Android\Sdk"),
        "C:\Android\Sdk")
    foreach ($c in $candidates) {
        if (-not $c) { continue }
        $expanded = $c
        if ($c.StartsWith("~")) { $expanded = Join-Path $HOME $c.Substring(1) }
        if (Test-Path (Join-Path $expanded "platform-tools")) {
            return (Resolve-Path $expanded).Path
        }
    }
    return $null
}

function Find-Ndk {
    param([string]$Sdk)
    if ($NdkRoot.trim()) {
        if (Test-Path $NdkRoot) { return (Resolve-Path $NdkRoot).Path }
        return $NdkRoot
    }
    foreach ($c in @($env:ANDROID_NDK_HOME, $env:ANDROID_NDK_ROOT, $env:ANDROID_NDK)) {
        if ($c -and (Test-Path $c)) { return (Resolve-Path $c).Path }
    }
    if ($Sdk) {
        $ndkDir = Join-Path $Sdk "ndk"
        if (Test-Path $ndkDir) {
            $versions = Get-ChildItem $ndkDir -Directory -ErrorAction SilentlyContinue |
                Sort-Object Name -Descending
            foreach ($v in $versions) {
                $tc = Join-Path $v.FullName "toolchains\llvm\prebuilt\windows-x86_64\bin"
                if (Test-Path $tc) { return $v.FullName }
            }
        }
    }
    return $null
}

function Find-CMake {
    param([string]$Sdk)
    $candidates = @()
    if ($Sdk) {
        $cmakeRoot = Join-Path $Sdk "cmake"
        if (Test-Path $cmakeRoot) {
            $candidates += Get-ChildItem $cmakeRoot -Directory -ErrorAction SilentlyContinue |
                Sort-Object Name -Descending |
                ForEach-Object { Join-Path $_.FullName "bin\cmake.exe" }
        }
    }
    foreach ($c in $candidates) { if (Test-Path $c) { return $c } }
    $fromPath = (Get-Command cmake.exe -ErrorAction SilentlyContinue)
    if ($fromPath) { return $fromPath.Source }
    return $null
}

function Find-Ninja {
    param([string]$Sdk)
    $candidates = @()
    if ($Sdk) {
        $cmakeRoot = Join-Path $Sdk "cmake"
        if (Test-Path $cmakeRoot) {
            $candidates += Get-ChildItem $cmakeRoot -Directory -ErrorAction SilentlyContinue |
                Sort-Object Name -Descending |
                ForEach-Object { Join-Path $_.FullName "bin\ninja.exe" }
        }
        $ndkDir = Join-Path $Sdk "ndk"
        if (Test-Path $ndkDir) {
            $versions = Get-ChildItem $ndkDir -Directory -ErrorAction SilentlyContinue
            foreach ($v in $versions) {
                $candidates += Join-Path $v.FullName "prebuilt\windows-x86_64\bin\ninja.exe"
            }
        }
    }
    foreach ($c in $candidates) { if (Test-Path $c) { return $c } }
    $fromPath = (Get-Command ninja.exe -ErrorAction SilentlyContinue)
    if ($fromPath) { return $fromPath.Source }
    return $null
}

function Find-Adb {
    $fromPath = (Get-Command adb.exe -ErrorAction SilentlyContinue)
    if ($fromPath) { return $fromPath.Source }
    if ($script:SdkRootDetected) {
        $legacy = Join-Path $script:SdkRootDetected "platform-tools\adb.exe"
        if (Test-Path $legacy) { return $legacy }
    }
    return $null
}

function Has-DotNet8 {
    try {
        $sdks = & dotnet --list-sdks 2>$null
        return @($sdks -match "^8\.\d+\.\d+").Count -gt 0
    } catch { return $false }
}

function Write-LocalProperties {
    $content = @()
    $content += "sdk.dir=$($script:SdkRootDetected -replace '\\', '/')"
    if ($script:NdkDetected) {
        $content += "ndk.dir=$($script:NdkDetected -replace '\\', '/')"
    }
    # cmake.dir is only meaningful for a real CMake install; the SDK-managed
    # cmake (pinned via `version = "4.1.2"` in build.gradle.kts) is resolved by
    # Gradle itself, so PATH shims are intentionally omitted.
    if ($script:CmakeDetected -match "Sdk(\\|/)cmake") {
        $cmakeDir = Split-Path (Split-Path $script:CmakeDetected -Parent) -Parent
        $content += "cmake.dir=$($cmakeDir -replace '\\', '/')"
    }
    $content += ""
    Set-Content -Path $LocalProps -Value $content -Encoding ASCII
    Write-Host "Wrote $LocalProps"
    Get-Content $LocalProps | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
}

function Get-GameFileStatus {
    $missing = @()
    foreach ($f in $GameFiles) {
        if (-not (Test-Path (Join-Path $AssetsDir $f))) { $missing += $f }
    }
    return @($missing)
}

function Stage-GameFiles([string]$sourceDir) {
    if (-not (Test-Path $sourceDir)) { Fail "Disc source directory not found: $sourceDir" }
    foreach ($f in $GameFiles) {
        $src = Join-Path $sourceDir $f
        if (-not (Test-Path $src)) {
            Fail "Disc source is missing $f (looked at $src). Expected RMCP01 PAL: main.dol + StaticR.rel."
        }
    }
    if (-not (Test-Path $AssetsDir)) { New-Item -ItemType Directory -Path $AssetsDir | Out-Null }
    foreach ($f in $GameFiles) {
        Copy-Item (Join-Path $sourceDir $f) (Join-Path $AssetsDir $f) -Force
        Write-Host "Staged $f from $sourceDir" -ForegroundColor Green
    }
}

function Invoke-Translator {
    Write-Step "Running translator" "Regenerates Wiicompiled\generated (shard sources, data init, shards.cmake)"
    if (-not (Has-DotNet8)) {
        Fail "The .NET 8 SDK is required to run the translator. Install it from https://dotnet.microsoft.com/download/dotnet/8.0"
    }
    if (-not (Test-Path $TranslatorDll)) {
        Write-Host "Building translator CLI..." -ForegroundColor Yellow
        Push-Location $Mkw
        try {
            & dotnet build $TranslatorCsproj -c Release
            if ($LASTEXITCODE -ne 0) { Fail "Translator build failed (dotnet build exit code $LASTEXITCODE)." }
        } finally { Pop-Location }
    }

    $missing = @(Get-GameFileStatus)
    if ($missing.Count -gt 0) {
        Fail "Translation requires $($missing -join ', ') in $AssetsDir first."
    }

    Push-Location $Mkw
    try {
        # 1. Translate the full call graph from the game entry point.
        Write-Host ""; Write-Host "Translating base (this can take several minutes)..." -ForegroundColor Yellow
        & dotnet $TranslatorDll translate-recursive 0x800060A4 `
            --project $Manifest `
            --output-metadata "$Generated\base_translation_output.json"
        if ($LASTEXITCODE -ne 0) { Fail "translate-recursive failed (exit $LASTEXITCODE)." }

        # 2. Emit the embedded .data/.rodata/.sdata section initializer + RuntimeConfig.h
        Write-Host ""; Write-Host "Generating data-section initializer / RuntimeConfig.h..." -ForegroundColor Yellow
        & dotnet $TranslatorDll generate-data-init --project $Manifest
        if ($LASTEXITCODE -ne 0) { Fail "generate-data-init failed (exit $LASTEXITCODE)." }

        # 3. Emit the CMake build graph (shards.cmake) covering generated + runtime sources.
        Write-Host ""; Write-Host "Emitting CMake shard build graph..." -ForegroundColor Yellow
        & dotnet $TranslatorDll emit-build-shards --project $Manifest
        if ($LASTEXITCODE -ne 0) { Fail "emit-build-shards failed (exit $LASTEXITCODE)." }
    } finally { Pop-Location }
}

# ---------------------------------------------------------------------------
# Pipeline
# ---------------------------------------------------------------------------

if ($script:SdkRootDetected -eq $null -and $Only -ne "" -and $Only -ne "Detect") {
    # A specific step was requested without Detect; load the toolchain first.
    $script:SdkRootDetected = Find-AndroidSdk
    $script:NdkDetected = Find-Ndk -Sdk $script:SdkRootDetected
    $script:CmakeDetected = Find-CMake -Sdk $script:SdkRootDetected
    $script:NinjaDetected = Find-Ninja -Sdk $script:SdkRootDetected
}

if ($Only -eq "" -or $Only -eq "Detect") {
    Write-Step "Detecting toolchain"
    $script:SdkRootDetected = Find-AndroidSdk
    if ($script:SdkRootDetected) {
        Write-Host "Android SDK: $script:SdkRootDetected" -ForegroundColor Green
    } else {
        $script:SdkRootDetected = $null
        Write-Host "Android SDK: not found" -ForegroundColor DarkYellow
    }
    $script:NdkDetected = Find-Ndk -Sdk $script:SdkRootDetected
    if ($script:NdkDetected) {
        Write-Host "Android NDK: $script:NdkDetected" -ForegroundColor Green
    } else {
        $script:NdkDetected = $null
        Write-Host "Android NDK: not found" -ForegroundColor DarkYellow
    }
    $script:CmakeDetected = Find-CMake -Sdk $script:SdkRootDetected
    if ($script:CmakeDetected) {
        Write-Host "CMake:       $script:CmakeDetected" -ForegroundColor Green
    } else {
        $script:CmakeDetected = $null
        Write-Host "CMake:       not found" -ForegroundColor DarkYellow
    }
    $script:NinjaDetected = Find-Ninja -Sdk $script:SdkRootDetected
    if ($script:NinjaDetected) {
        Write-Host "Ninja:       $script:NinjaDetected" -ForegroundColor Green
    } else {
        $script:NinjaDetected = $null
        Write-Host "Ninja:       not found" -ForegroundColor DarkYellow
    }
    if (Has-DotNet8) { Write-Host ".NET 8:      OK" -ForegroundColor Green }
    else { Write-Host ".NET 8:      not found (needed only to re-run the translator)" -ForegroundColor DarkYellow }

    if (-not $script:SdkRootDetected) {
        Fail "Android SDK not found. Set ANDROID_HOME, install to C:\Android\Sdk, or pass -SdkRoot."
    }
    if (-not $script:NdkDetected) {
        Fail "Android NDK 28 not found under the SDK. Install via Studio SDK Manager or sdkmanager."
    }
}

if ($Only -eq "" -or $Only -eq "LocalProperties") {
    Write-Step "Writing local.properties"
    Write-LocalProperties
}

if ($Only -eq "" -or $Only -eq "Assets") {
    Write-Step "Staging MKW disc files (RMCP01 PAL)"
    $missing = @(Get-GameFileStatus)
    if ($missing.Count -gt 0) {
        Write-Host "Missing in $AssetsDir : $($missing -join ', ')" -ForegroundColor DarkYellow
        if ($DiscSource) {
            Write-Host "Copying from -DiscSource $DiscSource"
            Stage-GameFiles $DiscSource
        } elseif (-not $SkipEnter) {
            $src = Read-Host "Directory containing your RMCP01 PAL main.dol + StaticR.rel (disc dump)"
            Stage-GameFiles $src
        } else {
            Fail "Game disc files are required in $AssetsDir. Provide -DiscSource <dir>."
        }
    } else {
        Write-Host "All game files present." -ForegroundColor Green
    }
}

if ($Only -eq "" -or $Only -eq "Translate") {
    $needsTranslate = $ForceTranslate -or (-not (Test-Path $ShardsCmake))
    if ($needsTranslate) {
        Invoke-Translator
    } else {
        Write-Step "Translator" "Existing generated shard build found at $ShardsCmake"
        Write-Host "Skipping translator (use -ForceTranslate to re-run)." -ForegroundColor DarkGray
    }
}

if ($Only -eq "" -or $Only -eq "Shards") {
    Write-Step "Building shard archive"
    if (-not (Test-Path $ShardsCmake)) {
        Fail "Missing $ShardsCmake — run translate step first."
    }
    # Point build-shards.bat at the detected toolchain via env so it never re-hardcodes.
    if ($script:NdkDetected) { $env:ANDROID_NDK_HOME = $script:NdkDetected }
    if ($script:CmakeDetected) { $env:CMAKE_BIN = $script:CmakeDetected }
    if ($script:NinjaDetected) { $env:NINJA_BIN = $script:NinjaDetected }
    Push-Location $Root
    try {
        cmd /c "build-shards.bat"
        if ($LASTEXITCODE -ne 0) { Fail "build-shards.bat failed (exit $LASTEXITCODE)." }
    } finally { Pop-Location }
}

if ($Only -eq "" -or $Only -eq "App") {
    Write-Step "Building APK"
    if (-not (Test-Path $ShardArchive)) {
        Fail "Missing shard archive $ShardArchive — build shards first."
    }
    Push-Location $Root
    try {
        if ($Release) {
            cmd /c "build-app-release.bat"
        } else {
            cmd /c "build-app-debug.bat"
        }
        if ($LASTEXITCODE -ne 0) { Fail "APK build failed (exit $LASTEXITCODE)." }
    } finally { Pop-Location }
}

if ($Only -eq "" -or $Only -eq "Install") {
    if (-not $Install) {
        if ($Only -ne "") { Write-Step "Install" "Skipped (-Install not passed)." }
        exit 0
    }
    Write-Step "Installing APK over ADB"
    $adb = Find-Adb
    if (-not $adb) { Fail "adb not found. Install platform-tools or add Android SDK to PATH." }
    $apk = if ($Release) {
        Join-Path $AndroidDir "app\build\outputs\apk\release\app-release.apk"
    } else {
        Join-Path $AndroidDir "app\build\outputs\apk\debug\app-debug.apk"
    }
    if (-not (Test-Path $apk)) { Fail "APK not found at $apk" }
    & $adb install -r $apk
    if ($LASTEXITCODE -ne 0) { Fail "ADB install failed." }
}

Write-Host ""
Write-Host "Done." -ForegroundColor Green