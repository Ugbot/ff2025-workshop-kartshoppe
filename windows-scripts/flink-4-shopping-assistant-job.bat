@echo off
REM Start Shopping Assistant Flink Job

setlocal enabledelayedexpansion

echo Starting Shopping Assistant Flink Job...
echo.

REM Set environment variables
if not defined KAFKA_BOOTSTRAP_SERVERS set KAFKA_BOOTSTRAP_SERVERS=localhost:19092

echo Kafka brokers: %KAFKA_BOOTSTRAP_SERVERS%
echo Model: gpt-4o-mini
echo.

REM Build the job if needed
echo Building Shopping Assistant...
call gradlew :flink-shopping-assistant:shadowJar
if %errorlevel% neq 0 (
    echo [ERROR] Build failed
    exit /b 1
)

echo.
echo Running Shopping Assistant Job...
echo.

REM Run the job
java --add-opens java.base/java.util=ALL-UNNAMED ^
  -cp flink-shopping-assistant\build\libs\flink-shopping-assistant-1.0.0-SNAPSHOT-all.jar ^
  com.ververica.composable_job.flink.assistant.ShoppingAssistantJob ^
  --kafka-brokers %KAFKA_BOOTSTRAP_SERVERS%

echo.
echo Shopping Assistant Job finished
pause
