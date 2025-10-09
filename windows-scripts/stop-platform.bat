@echo off
REM ==============================================================================
REM Shutdown Script: Stop All KartShoppe Services
REM ==============================================================================

setlocal enabledelayedexpansion

echo.
echo ========================================================================
echo.
echo            Stopping KartShoppe Platform
echo.
echo ========================================================================
echo.

REM Stop processes on port 3000 (Frontend - if running separately)
echo Stopping Frontend...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :3000 ^| findstr LISTENING') do (
    taskkill /F /PID %%a >nul 2>&1
)
echo [OK] Frontend stopped

REM Stop processes on port 8081 (Quarkus API)
echo Stopping Quarkus API...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :8081 ^| findstr LISTENING') do (
    taskkill /F /PID %%a >nul 2>&1
)
echo [OK] Quarkus API stopped

REM Kill any running Gradle daemons
echo Stopping Gradle daemons...
call gradlew --stop >nul 2>&1
echo [OK] Gradle daemons stopped

REM Stop Docker Compose services
echo Stopping Docker services...
docker compose down
echo [OK] Docker services stopped

REM Clean up PID files
if exist .pids (
    rmdir /s /q .pids
)

REM Clean up temporary log files
if exist logs\temp (
    rmdir /s /q logs\temp
)

echo.
echo ========================================================================
echo           All services stopped successfully!
echo ========================================================================
echo.
echo To start again, run: start-training-platform.bat
echo.
pause
