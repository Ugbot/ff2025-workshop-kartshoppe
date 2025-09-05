@echo off
setlocal EnableDelayedExpansion

REM ================================================
REM KartShoppe Setup Script for Windows (Batch)
REM ================================================

echo ======================================================
echo    KartShoppe Setup and Launch Script for Windows
echo ======================================================
echo.

REM ================================================
REM STEP 1: Check Docker
REM ================================================
echo ============================================
echo Step 1: Checking Docker Desktop
echo ============================================
echo.

docker --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Docker is not installed or not in PATH!
    echo.
    echo Please install Docker Desktop from:
    echo   https://www.docker.com/products/docker-desktop
    echo.
    echo After installation:
    echo   1. Start Docker Desktop
    echo   2. Wait for it to fully start
    echo   3. Run this script again
    echo.
    pause
    exit /b 1
)

echo [OK] Docker is installed

docker ps >nul 2>&1
if %errorlevel% neq 0 (
    echo [WARNING] Docker is installed but not running
    echo.
    echo Trying to start Docker Desktop...
    
    REM Try to start Docker Desktop
    if exist "%ProgramFiles%\Docker\Docker\Docker Desktop.exe" (
        start "" "%ProgramFiles%\Docker\Docker\Docker Desktop.exe"
        echo Waiting 30 seconds for Docker to start...
        timeout /t 30 /nobreak >nul
        
        docker ps >nul 2>&1
        if %errorlevel% neq 0 (
            echo [ERROR] Docker failed to start
            echo Please start Docker Desktop manually and run this script again
            pause
            exit /b 1
        )
        echo [OK] Docker is now running
    ) else (
        echo [ERROR] Could not find Docker Desktop
        echo Please start Docker Desktop manually and run this script again
        pause
        exit /b 1
    )
) else (
    echo [OK] Docker is running
)
echo.

REM ================================================
REM STEP 2: Check Java
REM ================================================
echo ============================================
echo Step 2: Checking Java 17
echo ============================================
echo.

java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java is not installed!
    echo.
    echo Automatic Java installation is not available in batch mode.
    echo Please either:
    echo.
    echo Option 1: Run the PowerShell version instead:
    echo   powershell -ExecutionPolicy Bypass -File setup.ps1
    echo.
    echo Option 2: Install Java 17 manually from:
    echo   https://adoptium.net/temurin/releases/?version=17
    echo.
    echo After installing Java, run this script again.
    echo.
    pause
    exit /b 1
)

REM Check Java version
for /f "tokens=3" %%i in ('java -version 2^>^&1 ^| findstr /i "version"') do set JAVA_VER=%%i
set JAVA_VER=%JAVA_VER:"=%

echo Java version found: %JAVA_VER%

REM Extract major version
for /f "delims=. tokens=1" %%v in ("%JAVA_VER%") do set JAVA_MAJOR=%%v
if "%JAVA_MAJOR%"=="1" (
    for /f "delims=. tokens=2" %%v in ("%JAVA_VER%") do set JAVA_MAJOR=%%v
)

if %JAVA_MAJOR% LSS 17 (
    echo [ERROR] Java %JAVA_MAJOR% found, but Java 17 or higher is required!
    echo.
    echo Please install Java 17 from:
    echo   https://adoptium.net/temurin/releases/?version=17
    echo.
    pause
    exit /b 1
)

echo [OK] Java %JAVA_MAJOR% is installed
echo.

REM ================================================
REM STEP 3: Build Project
REM ================================================
echo ============================================
echo Step 3: Building the Project
echo ============================================
echo.

echo Running Gradle build (this might take a few minutes)...

if not exist gradlew.bat (
    echo [ERROR] gradlew.bat not found!
    echo Make sure you're running this script from the project root directory.
    pause
    exit /b 1
)

call gradlew.bat build -x test --no-daemon >nul 2>&1
if %errorlevel% neq 0 (
    echo [WARNING] Build had some issues, but continuing...
) else (
    echo [OK] Build successful
)
echo.

REM ================================================
REM STEP 4: Launch Application
REM ================================================
echo ============================================
echo Step 4: Launching KartShoppe
echo ============================================
echo.

echo ========================================================
echo   Setup Complete! Starting KartShoppe...
echo ========================================================
echo.
echo The application will be available at:
echo   http://localhost:8080/kartshoppe/
echo.
echo What's happening:
echo   - Starting Redpanda (Kafka) in Docker
echo   - Creating Kafka topics automatically
echo   - Launching Flink inventory job
echo   - Starting Quarkus API server
echo   - Serving KartShoppe frontend
echo.
echo Press Ctrl+C to stop all services
echo.

REM Check for --no-flink parameter
set NO_FLINK=false
if "%1"=="--no-flink" set NO_FLINK=true

REM Start the application
if "%NO_FLINK%"=="true" (
    echo Starting without Flink inventory job...
    call gradlew.bat :quarkus-api:quarkusDev -Dflink.jobs.inventory.enabled=false
) else (
    call gradlew.bat :quarkus-api:quarkusDev
)

endlocal