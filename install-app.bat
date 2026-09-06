@echo off
setlocal

echo ===================================================
echo [WiiCompiled Android] ADB APK Installer
echo ===================================================

set SCRIPT_DIR=%~dp0
set MKW_DIR=%SCRIPT_DIR%android
set RELEASE_APK=%MKW_DIR%\app\build\outputs\apk\release\app-release.apk
set DEBUG_APK=%MKW_DIR%\app\build\outputs\apk\debug\app-debug.apk

echo.
echo Available APK Builds:
echo ---------------------------------------------------
if exist "%RELEASE_APK%" (
    for %%F in ("%RELEASE_APK%") do (
        echo [1] Release APK:
        echo     Path:      %%~fF
        echo     Size:      %%~zF bytes
        echo     Timestamp: %%~tF
    )
) else (
    echo [1] Release APK: (Not built yet)
)

echo.
if exist "%DEBUG_APK%" (
    for %%F in ("%DEBUG_APK%") do (
        echo [2] Debug APK:
        echo     Path:      %%~fF
        echo     Size:      %%~zF bytes
        echo     Timestamp: %%~tF
    )
) else (
    echo [2] Debug APK:   (Not built yet)
)
echo ---------------------------------------------------
echo.

echo Connected ADB Devices:
adb devices
echo.

set /p CHOICE="Select APK to install [1 for Release, 2 for Debug, default=1]: "

if "%CHOICE%"=="2" (
    set TARGET_APK=%DEBUG_APK%
    set VARIANT=Debug
) else (
    set TARGET_APK=%RELEASE_APK%
    set VARIANT=Release
)

if not exist "%TARGET_APK%" (
    echo.
    echo [ERROR] %VARIANT% APK does not exist at:
    echo         %TARGET_APK%
    echo Please build it first using build-app-%VARIANT:~0,1%%VARIANT:~1%.bat or build-app.bat.
    exit /b 1
)

echo.
echo Installing %VARIANT% APK to connected device...
adb install -r "%TARGET_APK%"

if errorlevel 1 (
    echo.
    echo [ERROR] Installation failed!
    exit /b 1
)

echo.
echo ===================================================
echo [SUCCESS] %VARIANT% APK installed successfully!
echo ===================================================
