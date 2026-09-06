@echo off
setlocal

echo ===================================================
echo [WiiCompiled Android] App Builder
echo ===================================================

echo Choose build variant:
echo   [1] Release (Optimized, recommended for gaming)
echo   [2] Debug   (Debug symbols, development)
echo.
set /p CHOICE="Select [1 or 2, default=1]: "

if "%CHOICE%"=="2" goto BUILD_DEBUG
goto BUILD_RELEASE

:BUILD_RELEASE
call "%~dp0build-app-release.bat" %*
exit /b %ERRORLEVEL%

:BUILD_DEBUG
call "%~dp0build-app-debug.bat" %*
exit /b %ERRORLEVEL%
