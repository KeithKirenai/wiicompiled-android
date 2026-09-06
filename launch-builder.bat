@echo off
setlocal
echo ===================================================
echo [WiiCompiled Android] Starting Desktop Builder GUI
echo ===================================================

set "SCRIPT_DIR=%~dp0"
if exist "%SCRIPT_DIR%Wiicompiled" (
    set "MKW_DIR=%SCRIPT_DIR%Wiicompiled"
) else (
    set "MKW_DIR=%SCRIPT_DIR%"
)

set "PROJECT=%MKW_DIR%\Launcher\WiiDiscExtractorGui\WiiDiscExtractorGui.csproj"
set "EXE=%MKW_DIR%\Launcher\WiiDiscExtractorGui\bin\Release\net8.0-windows\WiiCompiledAndroidBuilder.exe"

where dotnet >nul 2>nul
if errorlevel 1 (
    echo [ERROR] .NET 8 SDK not found on PATH.
    echo Please install the .NET 8 SDK from https://dotnet.microsoft.com/download/dotnet/8.0
    pause
    exit /b 1
)

if not exist "%EXE%" (
    echo Building WiiCompiled Android Builder...
    dotnet build "%PROJECT%" -c Release --nologo -v q
    if errorlevel 1 (
        echo [ERROR] Failed to compile WiiCompiled Android Builder.
        pause
        exit /b 1
    )
)

echo Launching WiiCompiled Android Builder...
start "" "%EXE%"
exit /b 0
