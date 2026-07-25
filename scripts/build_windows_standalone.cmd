@echo off
setlocal
cd /d "%~dp0\.."

if defined JAVA_HOME (
  set "PATH=%JAVA_HOME%\bin;%PATH%"
)

py -3 scripts\package_windows_64.py
exit /b %ERRORLEVEL%
