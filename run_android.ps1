<#
.SYNOPSIS
    WiiCompiled Android Automation Helper
.DESCRIPTION
    Builds, installs, runs, captures screenshots, and profiles WiiCompiled on Android.
.EXAMPLE
    .\run_android.ps1          # Builds, installs, and launches the game
    .\run_android.ps1 build    # Compiles the APK only
    .\run_android.ps1 install  # Installs the latest APK
    .\run_android.ps1 launch   # Launches the game on the device
    .\run_android.ps1 shot     # Takes and pulls a screenshot
    .\run_android.ps1 log      # Streams logcat for WiiCompiled
    .\run_android.ps1 perf     # Shows live CPU and thermal statistics
#>
param(
    [Parameter(Position=0)]
    [ValidateSet("all", "build", "install", "run", "launch", "shot", "screenshot", "log", "logcat", "perf", "profile")]
    [string]$Action = "all"
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$AndroidDir = Join-Path $ScriptDir "android"
$ApkPath = Join-Path $AndroidDir "app\build\outputs\apk\debug\app-debug.apk"
$PackageName = "com.wiicompiled.mkw"
$ActivityName = ".MainActivity"

function Test-Device {
    $devices = & adb devices | Select-String "device$"
    if (-not $devices) {
        Write-Warning "No authorized Android device detected. Please check your USB cable and USB debugging settings."
        return $false
    }
    return $true
}

switch ($Action) {
    "build" {
        Write-Host "Compiling Android APK with Gradle..." -ForegroundColor Cyan
        Push-Location $AndroidDir
        try {
            cmd /c "gradlew.bat assembleDebug"
        } finally {
            Pop-Location
        }
    }
    "install" {
        if (-not (Test-Device)) { exit 1 }
        Write-Host "Installing APK to device: $ApkPath" -ForegroundColor Cyan
        & adb install -r "$ApkPath"
    }
    { $_ -in "run", "launch" } {
        if (-not (Test-Device)) { exit 1 }
        Write-Host "Launching $PackageName..." -ForegroundColor Green
        & adb shell am force-stop $PackageName
        & adb shell am start -n "$PackageName/$ActivityName"
    }
    { $_ -in "shot", "screenshot" } {
        if (-not (Test-Device)) { exit 1 }
        $timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
        $outFile = Join-Path $ScriptDir "screenshot_$timestamp.png"
        Write-Host "Capturing screenshot..." -ForegroundColor Cyan
        & adb shell screencap -p /sdcard/screen_temp.png
        & adb pull /sdcard/screen_temp.png "$outFile"
        Write-Host "Saved screenshot to: $outFile" -ForegroundColor Green
    }
    { $_ -in "log", "logcat" } {
        if (-not (Test-Device)) { exit 1 }
        Write-Host "Streaming WiiCompiled logcat (Ctrl+C to stop)..." -ForegroundColor Yellow
        & adb logcat -s WiiCompiled_NDK:I WiiCompiled:I
    }
    { $_ -in "perf", "profile" } {
        if (-not (Test-Device)) { exit 1 }
        Write-Host "--- CPU Top Consumers ---" -ForegroundColor Cyan
        & adb shell dumpsys cpuinfo | Select-Object -First 20
        Write-Host "--- Thermal State ---" -ForegroundColor Cyan
        & adb shell "cat /sys/class/thermal/thermal_zone0/temp /sys/class/thermal/thermal_zone1/temp"
    }
    "all" {
        Write-Host "=== [1/3] Building APK ===" -ForegroundColor Cyan
        Push-Location $AndroidDir
        try {
            cmd /c "gradlew.bat assembleDebug"
        } finally {
            Pop-Location
        }

        if (-not (Test-Device)) { exit 1 }
        Write-Host "=== [2/3] Installing APK ===" -ForegroundColor Cyan
        & adb install -r "$ApkPath"

        Write-Host "=== [3/3] Launching Game ===" -ForegroundColor Green
        & adb shell am force-stop $PackageName
        & adb shell am start -n "$PackageName/$ActivityName"
        Write-Host "Done! WiiCompiled is running." -ForegroundColor Green
    }
}
