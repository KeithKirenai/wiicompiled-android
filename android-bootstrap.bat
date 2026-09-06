@echo off
setlocal
rem WiiCompiled Android - one-shot clone-to-phone bootstrap wrapper.
rem Forwards all arguments to android-bootstrap.ps1.

set "SCRIPT_DIR=%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%android-bootstrap.ps1" %*
exit /b %ERRORLEVEL%