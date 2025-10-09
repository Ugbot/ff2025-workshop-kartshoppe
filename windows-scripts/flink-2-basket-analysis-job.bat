@echo off
REM Start the Basket Analysis Flink Job

setlocal enabledelayedexpansion

echo Starting Basket Analysis Flink Job...
echo.

REM Set environment variables
if not defined KAFKA_BOOTSTRAP_SERVERS set KAFKA_BOOTSTRAP_SERVERS=localhost:19092

echo Kafka brokers: %KAFKA_BOOTSTRAP_SERVERS%
echo.

REM Build the job if needed
echo Building Basket Analysis Flink Job...
call gradlew :flink-recommendations:shadowJar
if %errorlevel% neq 0 (
    echo [ERROR] Build failed
    exit /b 1
)

echo.
echo Running Basket Analysis Job...
echo.

REM Run the job
java --add-opens java.base/java.util=ALL-UNNAMED ^
  -cp flink-recommendations\build\libs\flink-recommendations-1.0.0-SNAPSHOT-all.jar ^
  com.ververica.composable_job.flink.recommendations.BasketAnalysisJob ^
  --kafka-brokers %KAFKA_BOOTSTRAP_SERVERS%

echo.
echo Basket Analysis Job finished
pause
