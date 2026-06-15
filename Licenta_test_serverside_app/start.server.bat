@echo off
title Network Inventory Dashboard Server
color 0A

echo ========================================================
echo   Starting Network Inventory Server...
echo ========================================================
echo.

:: Check if Java is installed
java -version >nul 2>&1
if %errorlevel% neq 0 (
    color 0C
    echo [ERROR] Java is not installed or not in your system PATH.
    echo Please install Java 11 or newer to run this server.
    echo.
    pause
    exit /b 1
)

echo [INFO] Java detected. Booting WildFly server environment...
echo [INFO] Please wait. The dashboard will be available at: http://localhost:8080/Licenta_test_serverside_app-1.0-SNAPSHOT
echo.

:: Run the embedded Maven wrapper to start WildFly
call mvnw.cmd wildfly:run

pause