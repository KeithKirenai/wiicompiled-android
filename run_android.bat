@echo off
setlocal

set SCRIPT_DIR=%~dp0
set PROJECT_DIR=%SCRIPT_DIR%
set ANDROID_DIR=%PROJECT_DIR%\android
set APK_PATH=%ANDROID_DIR%\app\build\outputs\apk\debug\app-debug.apk
set PACKAGE_NAME=com.wiicompiled.mkw
set ACTIVITY_NAME=.MainActivity

echo =======================================================
echo   WiiCompiled Android - Build, Install ^& Launch Tool
echo =======================================================
echo.

if /i "%1"=="build" goto do_build
if /i "%1"=="install" goto do_install
if /i "%1"=="run" goto do_run
if /i "%1"=="launch" goto do_run
if /i "%1"=="screenshot" goto do_screenshot
if /i "%1"=="shot" goto do_screenshot
if /i "%1"=="logcat" goto do_logcat
if /i "%1"=="log" goto do_logcat
if /i "%1"=="profile" goto do_profile
if /i "%1"=="perf" goto do_profile

:do_all
echo [1/3] Building APK with Gradle...
cd /d "%ANDROID_DIR%"
call gradlew.bat assembleDebug
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build failed!
    exit /b %ERRORLEVEL%
)

:do_install
echo [2/3] Installing APK to connected device...
adb install -r "%APK_PATH%"
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] ADB install failed! Please verify device is connected.
    exit /b %ERRORLEVEL%
)

:do_run
echo [3/3] Launching WiiCompiled...
adb shell am force-stop %PACKAGE_NAME%
adb shell am start -n "%PACKAGE_NAME%/%ACTIVITY_NAME%"
echo Done! Game is running.
exit /b 0

:do_build
echo Building APK with Gradle...
cd /d "%ANDROID_DIR%"
call gradlew.bat assembleDebug
exit /b %ERRORLEVEL%

:do_screenshot
set TIMESTAMP=%date:~-4,4%%date:~-10,2%%date:~-7,2%_%time:~0,2%%time:~3,2%%time:~6,2%
set TIMESTAMP=%TIMESTAMP: =0%
set OUTPUT_FILE=%PROJECT_DIR%\screenshot_%TIMESTAMP%.png
echo Capturing screenshot from device...
adb shell screencap -p /sdcard/screen_temp.png
adb pull /sdcard/screen_temp.png "%OUTPUT_FILE%"
echo Screenshot saved to: %OUTPUT_FILE%
exit /b 0

:do_logcat
echo Streaming WiiCompiled logcat (Press Ctrl+C to stop)...
adb logcat -s WiiCompiled_NDK:I WiiCompiled:I
exit /b 0

:do_profile
echo Dumping profiling info and thermal stats...
echo --- CPU USAGE ---
adb shell dumpsys cpuinfo | head -20
echo --- THERMAL STATUS ---
adb shell "for z in /sys/class/thermal/thermal_zone*/temp; do echo \; cat \; done 2>/dev/null | head -10"
exit /b 0
