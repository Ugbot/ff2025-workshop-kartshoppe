@echo off
REM Start the Inventory Management Flink Job

setlocal enabledelayedexpansion

echo Starting Inventory Management Flink Job...
echo.

REM Set environment variables
if not defined KAFKA_BOOTSTRAP_SERVERS set KAFKA_BOOTSTRAP_SERVERS=localhost:19092
if not defined INITIAL_PRODUCTS_FILE set INITIAL_PRODUCTS_FILE=%~dp0..\data\initial-products.json

echo Using products file: %INITIAL_PRODUCTS_FILE%
echo Kafka brokers: %KAFKA_BOOTSTRAP_SERVERS%
echo.

REM Build the job if needed
echo Building Inventory Flink Job...
call gradlew :flink-inventory:shadowJar
if %errorlevel% neq 0 (
    echo [ERROR] Build failed
    exit /b 1
)

echo.
echo Running Inventory Management Job...
echo.

REM Run the job
java --add-opens java.base/java.util=ALL-UNNAMED ^
  -cp flink-inventory\build\libs\flink-inventory.jar ^
  com.ververica.composable_job.flink.inventory.InventoryManagementJob

echo.
echo Inventory Management Job finished
pause
