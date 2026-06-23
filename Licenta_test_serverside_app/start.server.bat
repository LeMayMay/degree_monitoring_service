@echo off
java -version >nul 2>&1
if %errorlevel% neq 0 (
    color 0C
    echo [ERROR] Java is not installed or not in your system PATH.
    echo Please install Java 11 or newer to run this server.
    echo.
    pause
    exit /b 1
)
call mvnw.cmd wildfly:run
pause