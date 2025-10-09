@echo off
REM ==============================================================================
REM KartShoppe Training Platform Startup
REM ==============================================================================
REM Starts the core platform WITHOUT any Flink jobs
REM - Redpanda (Kafka)
REM - Quarkus API with integrated frontend (Quinoa)
REM
REM Flink jobs are started separately during training modules
REM ==============================================================================

setlocal enabledelayedexpansion

cls

echo.
echo ========================================================================
echo.
echo         Starting KartShoppe Training Platform
echo.
echo ========================================================================
echo.

REM Create directories for logs and PIDs
if not exist logs mkdir logs
if not exist .pids mkdir .pids

REM ==============================================================================
REM Step 1: Check Prerequisites
REM ==============================================================================

echo.
echo ========================================================================
echo Step 1/5: Checking Prerequisites
echo ========================================================================
echo.

REM Check Docker
docker info >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Docker is not running
    echo Please start Docker Desktop and try again
    exit /b 1
)
echo [OK] Docker is running

REM Check Java version
java -version 2>&1 | findstr /C:"version \"17" >nul
if %errorlevel% neq 0 (
    java -version 2>&1 | findstr /C:"version \"21" >nul
    if !errorlevel! neq 0 (
        echo [WARNING] Java 17+ is required for Quarkus
        java -version
        echo.
        echo Please switch to Java 17 or higher
        exit /b 1
    )
)
echo [OK] Java 17+ detected

REM Check Node.js
where node >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Node.js is not installed
    exit /b 1
)
echo [OK] Node.js found

echo.

REM ==============================================================================
REM Step 2: Start Infrastructure (Redpanda)
REM ==============================================================================

echo.
echo ========================================================================
echo Step 2/5: Starting Infrastructure
echo ========================================================================
echo.

echo Starting Redpanda (Kafka)...
docker compose up -d redpanda redpanda-console

echo Waiting for Redpanda to be healthy...
set /a count=0
:wait_redpanda
timeout /t 2 /nobreak >nul
docker compose ps redpanda | findstr "healthy" >nul
if %errorlevel% neq 0 (
    set /a count+=1
    if !count! gtr 30 (
        echo [ERROR] Redpanda failed to start
        exit /b 1
    )
    echo .
    goto wait_redpanda
)
echo.
echo [OK] Redpanda is healthy

echo.

REM ==============================================================================
REM Step 3: Create Kafka Topics
REM ==============================================================================

echo.
echo ========================================================================
echo Step 3/5: Creating Kafka Topics
echo ========================================================================
echo.

REM Using the 0-setup-topics.sh script if available, otherwise create inline
if exist 0-setup-topics.sh (
    bash 0-setup-topics.sh
) else (
    echo Creating topics directly...
    docker exec redpanda rpk topic create websocket_fanout --brokers localhost:19092 --partitions 3 2>nul
    docker exec redpanda rpk topic create processing_fanout --brokers localhost:19092 --partitions 3 2>nul
    docker exec redpanda rpk topic create ecommerce_events --brokers localhost:19092 --partitions 3 2>nul
    docker exec redpanda rpk topic create ecommerce_processing_fanout --brokers localhost:19092 --partitions 3 2>nul
    docker exec redpanda rpk topic create product_updates --brokers localhost:19092 --partitions 3 2>nul
    docker exec redpanda rpk topic create products --brokers localhost:19092 --partitions 3 2>nul
    docker exec redpanda rpk topic create inventory_updates --brokers localhost:19092 --partitions 3 2>nul
    docker exec redpanda rpk topic create shopping-cart-events --brokers localhost:19092 --partitions 3 2>nul
    docker exec redpanda rpk topic create basket-patterns --brokers localhost:19092 --partitions 3 2>nul
    docker exec redpanda rpk topic create product-recommendations --brokers localhost:19092 --partitions 3 2>nul
    docker exec redpanda rpk topic create shopping-assistant-chat --brokers localhost:19092 --partitions 3 2>nul
    docker exec redpanda rpk topic create assistant-responses --brokers localhost:19092 --partitions 3 2>nul
)

echo [OK] Topics created

echo.

REM ==============================================================================
REM Step 4: Prepare Frontend
REM ==============================================================================

echo.
echo ========================================================================
echo Step 4/5: Preparing Frontend
echo ========================================================================
echo.

REM Install frontend dependencies if needed (Quinoa will use these)
if not exist kartshoppe-frontend\node_modules (
    echo Installing frontend dependencies (required for Quinoa)...
    cd kartshoppe-frontend
    call npm install
    cd ..
    echo [OK] Frontend dependencies installed
) else (
    echo [OK] Frontend dependencies already installed
)

REM Build required modules
echo Building models module...
call gradlew :models:build -q
echo [OK] Models module built

echo.

REM ==============================================================================
REM Step 5: Start Quarkus with Integrated Frontend (Quinoa)
REM ==============================================================================

echo.
echo ========================================================================
echo Step 5/5: Starting Quarkus API + Frontend (Quinoa)
echo ========================================================================
echo.

echo Starting Quarkus in development mode...
echo (Frontend will be integrated via Quinoa)
echo.

REM Start Quarkus in new window
start "Quarkus API" cmd /c "gradlew :quarkus-api:quarkusDev"

echo Waiting for Quarkus to start (this may take 30-60 seconds)...

REM Wait for health check
set /a count=0
:wait_quarkus
timeout /t 3 /nobreak >nul
curl -s http://localhost:8081/q/health/ready >nul 2>&1
if %errorlevel% neq 0 (
    set /a count+=1
    if !count! gtr 40 (
        echo [ERROR] Quarkus failed to start - check logs
        goto show_final_status
    )
    echo .
    goto wait_quarkus
)

echo.
echo [OK] Quarkus API is ready!

REM ==============================================================================
REM Final Status
REM ==============================================================================

:show_final_status
echo.
echo ========================================================================
echo.
echo           Platform Started Successfully!
echo.
echo ========================================================================
echo.
echo Access Points:
echo   KartShoppe App:       http://localhost:8081
echo   Quarkus Dev UI:       http://localhost:8081/q/dev
echo   API Endpoints:        http://localhost:8081/api/*
echo   Redpanda Console:     http://localhost:8085
echo.
echo Services Running:
echo   [OK] Redpanda (Kafka):     Port 19092
echo   [OK] Quarkus + Frontend:   Port 8081
echo   [OK] Redpanda Console:     Port 8085
echo.
echo Training Modules (Run Separately):
echo   Module 1 - Inventory:      flink-1-inventory-job.bat
echo   Module 2 - Basket Analysis: flink-2-basket-analysis-job.bat
echo   Module 3 - Hybrid Source:   flink-3-hybrid-source-job.bat
echo   Module 4 - AI Assistant:    flink-4-shopping-assistant-job.bat
echo.
echo Logs:
echo   Quarkus: Check the separate Quarkus window
echo   Docker:  docker compose logs -f
echo.
echo ========================================================================
echo To stop the platform: stop-platform.bat
echo ========================================================================
echo.
echo Press Ctrl+C to exit this script (services will keep running)
pause
