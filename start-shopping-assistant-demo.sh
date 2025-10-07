#!/bin/bash

# Complete Shopping Assistant Demo Startup Script
# This starts all components needed for the KartShoppe shopping assistant

set -e

echo "=========================================="
echo "  Shopping Assistant Demo Startup"
echo "=========================================="
echo ""

# Check if OPENAI_API_KEY is set
if [ -z "$OPENAI_API_KEY" ]; then
    echo "❌ ERROR: OPENAI_API_KEY environment variable is not set"
    echo "Please set it with: export OPENAI_API_KEY='your-key-here'"
    exit 1
fi

echo "✅ OPENAI_API_KEY is set"
echo ""

# Function to wait for a service to be ready
wait_for_kafka() {
    echo "Waiting for Kafka to be ready..."
    max_attempts=30
    attempt=0
    while [ $attempt -lt $max_attempts ]; do
        if docker exec redpanda-1 rpk cluster info &>/dev/null; then
            echo "✅ Kafka is ready"
            return 0
        fi
        attempt=$((attempt + 1))
        echo "  Attempt $attempt/$max_attempts..."
        sleep 2
    done
    echo "❌ Kafka failed to start"
    return 1
}

# Step 1: Start Kafka/Redpanda
echo "Step 1: Starting Kafka (Redpanda)..."
if docker ps | grep -q redpanda; then
    echo "✅ Redpanda is already running"
else
    echo "Starting Redpanda container..."
    docker compose up -d redpanda
    wait_for_kafka
fi
echo ""

# Step 2: Build Shopping Assistant Flink Job
echo "Step 2: Building Shopping Assistant Flink Job..."
./gradlew :flink-shopping-assistant:shadowJar
echo "✅ Shopping Assistant built"
echo ""

# Step 3: Start Shopping Assistant Job in background
echo "Step 3: Starting Shopping Assistant Flink Job..."
KAFKA_BOOTSTRAP_SERVERS=localhost:19092 \
java -cp flink-shopping-assistant/build/libs/flink-shopping-assistant-1.0.0-SNAPSHOT-all.jar \
  com.ververica.composable_job.flink.assistant.ShoppingAssistantJob \
  --kafka-brokers localhost:19092 \
  > logs/shopping-assistant.log 2>&1 &

ASSISTANT_PID=$!
echo "✅ Shopping Assistant running (PID: $ASSISTANT_PID)"
echo "   Logs: tail -f logs/shopping-assistant.log"
echo ""

# Step 4: Build and Start Quarkus API
echo "Step 4: Starting Quarkus API..."
cd quarkus-api
./gradlew quarkusDev &
QUARKUS_PID=$!
cd ..
echo "✅ Quarkus API starting (PID: $QUARKUS_PID)"
echo "   URL: http://localhost:8080"
echo ""

# Step 5: Start Frontend
echo "Step 5: Starting KartShoppe Frontend..."
cd kartshoppe-frontend
npm install --silent
npm run dev &
FRONTEND_PID=$!
cd ..
echo "✅ Frontend starting (PID: $FRONTEND_PID)"
echo "   URL: http://localhost:3000"
echo ""

# Save PIDs for cleanup
cat > .shopping-assistant-pids <<EOF
ASSISTANT_PID=$ASSISTANT_PID
QUARKUS_PID=$QUARKUS_PID
FRONTEND_PID=$FRONTEND_PID
EOF

echo "=========================================="
echo "  🎉 Shopping Assistant Demo is Starting!"
echo "=========================================="
echo ""
echo "Services:"
echo "  • Kafka (Redpanda): localhost:19092"
echo "  • Shopping Assistant: Running in background"
echo "  • Quarkus API: http://localhost:8080"
echo "  • Frontend: http://localhost:3000"
echo ""
echo "WebSocket endpoint: ws://localhost:8080/ws/chat"
echo "REST endpoint: http://localhost:8080/api/chat/shopping-assistant"
echo ""
echo "To stop all services, run:"
echo "  ./stop-shopping-assistant-demo.sh"
echo ""
echo "To view logs:"
echo "  tail -f logs/shopping-assistant.log"
echo ""
