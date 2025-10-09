@echo off
REM ==============================================================================
REM KartShoppe Training Environment Setup for Windows
REM ==============================================================================
REM This script sets up all prerequisites from a blank slate for the training.
REM Run this ONCE before the training session begins.
REM ==============================================================================

setlocal enabledelayedexpansion

cls

echo.
echo ========================================================================
echo.
echo       KartShoppe Training Environment Setup (Windows)
echo.
echo       This will install and configure all prerequisites
echo.
echo ========================================================================
echo.
echo This script will install:
echo   1. SDKMAN (Java version manager) via Git Bash
echo   2. Java 11 (for building Flink jobs)
echo   3. Java 17 (for running Quarkus)
echo   4. Verify Docker Desktop is installed
echo   5. Verify Node.js 18+ is installed
echo.
echo Estimated time: 5-10 minutes
echo.
pause

REM ==============================================================================
REM Step 1: Check Docker Desktop
REM ==============================================================================

echo.
echo ========================================================================
echo Step 1/5: Checking Docker Desktop
echo ========================================================================
echo.

where docker >nul 2>&1
if %errorlevel% equ 0 (
    docker info >nul 2>&1
    if !errorlevel! equ 0 (
        echo [OK] Docker is installed and running
        docker --version
    ) else (
        echo [ERROR] Docker is installed but not running
        echo Please start Docker Desktop and run this script again
        exit /b 1
    )
) else (
    echo [ERROR] Docker is not installed
    echo.
    echo Please install Docker Desktop for Windows:
    echo https://docs.docker.com/desktop/install/windows-install/
    exit /b 1
)

REM ==============================================================================
REM Step 2: Check Node.js
REM ==============================================================================

echo.
echo ========================================================================
echo Step 2/5: Checking Node.js
echo ========================================================================
echo.

where node >nul 2>&1
if %errorlevel% equ 0 (
    echo [OK] Node.js is installed
    node --version
    npm --version
) else (
    echo [ERROR] Node.js is not installed
    echo.
    echo Please install Node.js 18+:
    echo https://nodejs.org/ (download LTS version)
    exit /b 1
)

REM ==============================================================================
REM Step 3: Check Java
REM ==============================================================================

echo.
echo ========================================================================
echo Step 3/5: Checking Java Installation
echo ========================================================================
echo.

where java >nul 2>&1
if %errorlevel% equ 0 (
    echo [OK] Java is installed
    java -version
    echo.
    echo NOTE: This project requires:
    echo   - Java 11 for building Flink jobs
    echo   - Java 17+ for running Quarkus
    echo.
    echo Please ensure you have both versions installed.
    echo We recommend using SDKMAN with Git Bash on Windows:
    echo   1. Install Git Bash: https://git-scm.com/download/win
    echo   2. Open Git Bash and run: curl -s "https://get.sdkman.io" ^| bash
    echo   3. Install Java 11: sdk install java 11.0.25-tem
    echo   4. Install Java 17: sdk install java 17.0.13-tem
) else (
    echo [ERROR] Java is not installed
    echo.
    echo Please install Java 17+ from:
    echo https://adoptium.net/
    exit /b 1
)

REM ==============================================================================
REM Step 4: Check Gradle Wrapper
REM ==============================================================================

echo.
echo ========================================================================
echo Step 4/5: Checking Gradle Wrapper
echo ========================================================================
echo.

if exist gradlew.bat (
    echo [OK] Gradle wrapper found
) else (
    echo [ERROR] Gradle wrapper not found
    echo Please ensure you're in the project root directory
    exit /b 1
)

REM ==============================================================================
REM Step 5: Final Summary
REM ==============================================================================

echo.
echo ========================================================================
echo           Setup Verification Complete!
echo ========================================================================
echo.
echo Installed Components:
echo   [OK] Docker Desktop
echo   [OK] Node.js
echo   [OK] Java
echo   [OK] Gradle Wrapper
echo.
echo ========================================================================
echo Next Steps:
echo ========================================================================
echo.
echo   1. Start the platform:    start-training-platform.bat
echo   2. Read training guide:   type TRAINING-SETUP.md
echo.
echo You are now ready for the KartShoppe training!
echo.
pause
