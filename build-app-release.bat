@echo off
setlocal

echo ===================================================
echo [WiiCompiled Android] Building Release APK
echo ===================================================

set SCRIPT_DIR=%~dp0
set MKW_DIR=%SCRIPT_DIR%android
set SHARD_PREBUILT=%MKW_DIR%\app\src\main\jniLibs\arm64-v8a\libmkw_base_shared.a

if exist "%SHARD_PREBUILT%" goto SHARDS_OK

echo [INFO] Prebuilt shard archive not found.
echo [INFO] Automatically triggering build-shards.bat first...
echo.
call "%SCRIPT_DIR%build-shards.bat"
if errorlevel 1 (
    echo [ERROR] Shard compilation failed! Cannot proceed with APK build.
    exit /b 1
)
echo.
echo [INFO] Shards successfully built. Proceeding with APK build...
echo.

:SHARDS_OK
echo [INFO] Found prebuilt shards archive:
echo        %SHARD_PREBUILT%
echo [INFO] Skipping shard compilation (fast build).

cd /d "%MKW_DIR%"

echo Building Release APK with Gradle...
call gradlew.bat assembleRelease %*

if errorlevel 1 (
    echo [ERROR] Gradle build failed!
    cd /d "%SCRIPT_DIR%"
    exit /b 1
)

cd /d "%SCRIPT_DIR%"

set APK_PATH=%MKW_DIR%\app\build\outputs\apk\release\app-release.apk

echo ===================================================
echo [WiiCompiled Android] Release build complete!
if exist "%APK_PATH%" (
    echo Output APK: %APK_PATH%
    for %%F in ("%APK_PATH%") do (
        echo Size:       %%~zF bytes
        echo Timestamp:  %%~tF
    )
)
echo ===================================================
