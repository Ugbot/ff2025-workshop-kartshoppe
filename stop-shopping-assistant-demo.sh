#!/bin/bash

# Stop all Shopping Assistant Demo services

echo "=========================================="
echo "  Stopping Shopping Assistant Demo"
echo "=========================================="
echo ""

# Read PIDs if file exists
if [ -f .shopping-assistant-pids ]; then
    source .shopping-assistant-pids

    if [ ! -z "$ASSISTANT_PID" ]; then
        echo "Stopping Shopping Assistant (PID: $ASSISTANT_PID)..."
        kill $ASSISTANT_PID 2>/dev/null || echo "  Already stopped"
    fi

    if [ ! -z "$QUARKUS_PID" ]; then
        echo "Stopping Quarkus API (PID: $QUARKUS_PID)..."
        kill $QUARKUS_PID 2>/dev/null || echo "  Already stopped"
    fi

    if [ ! -z "$FRONTEND_PID" ]; then
        echo "Stopping Frontend (PID: $FRONTEND_PID)..."
        kill $FRONTEND_PID 2>/dev/null || echo "  Already stopped"
    fi

    rm .shopping-assistant-pids
else
    echo "No PID file found, attempting to stop by process name..."
    pkill -f "ShoppingAssistantJob" || true
    pkill -f "quarkusDev" || true
    pkill -f "vite" || true
fi

echo ""
echo "✅ All services stopped"
echo ""
echo "To stop Kafka/Redpanda:"
echo "  docker compose down"
echo ""
