#!/bin/bash

# Start Shopping Assistant Flink Job
# This script builds and runs the Shopping Assistant job

set -e

echo "Building Shopping Assistant..."
./gradlew :flink-shopping-assistant:shadowJar

echo ""
echo "Starting Shopping Assistant Job..."
echo "Kafka: ${KAFKA_BOOTSTRAP_SERVERS:-localhost:19092}"
echo "Model: gpt-4o-mini"
echo ""

KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-localhost:19092} \
java --add-opens java.base/java.util=ALL-UNNAMED \
  -cp flink-shopping-assistant/build/libs/flink-shopping-assistant-1.0.0-SNAPSHOT-all.jar \
  com.ververica.composable_job.flink.assistant.ShoppingAssistantJob \
  --kafka-brokers ${KAFKA_BOOTSTRAP_SERVERS:-localhost:19092}
