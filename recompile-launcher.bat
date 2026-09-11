@echo off
setlocal
echo ===================================================
echo [WiiCompiled Android] Rebuilding Setup Launcher
echo ===================================================

set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

set "PROJECT=%SCRIPT_DIR%\Launcher\WiiCompiled.Setup.Android\WiiCompiled.Setup.Android\WiiCompiled.Setup.Android.csproj"

where dotnet >nul 2>nul
if errorlevel 1 (
    echo [ERROR] .NET 8 SDK not found on PATH.
    echo Please install the .NET 8 SDK from https://dotnet.microsoft.com/download/dotnet/8.0
    pause
    exit /b 1
)

echo Recompiling Setup Launcher (Release)...
dotnet build "%PROJECT%" -c Release --nologo
if errorlevel 1 (
    echo [ERROR] Failed to compile Setup Launcher.
    pause
    exit /b 1
)

echo.
echo Rebuild complete. You can now launch it with launcher.bat
pause
exit /b 0
