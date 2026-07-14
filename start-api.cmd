@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"

set "JAVA_ENCODING_ARGS=-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"

where java >nul 2>nul
if errorlevel 1 (
    echo Java was not found. Please install JDK 21 and make sure java is in PATH.
    pause
    exit /b 1
)

echo Starting xianyu-api...
echo Working directory: %CD%
echo API URL: http://localhost:12400
echo.

call ".\mvnw.cmd" spring-boot:run "-Dspring-boot.run.jvmArguments=%JAVA_ENCODING_ARGS%"
set "EXIT_CODE=%ERRORLEVEL%"

echo.
echo xianyu-api stopped with code %EXIT_CODE%.
pause
exit /b %EXIT_CODE%
