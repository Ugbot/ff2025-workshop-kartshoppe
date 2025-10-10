#!/bin/bash
# Initialize all required Kafka topics for the KartShoppe platform
# This script should be run before starting any Flink jobs

set -e

echo "🔧 Setting up Kafka topics..."

# Kafka broker
KAFKA_BROKER=${KAFKA_BROKER:-localhost:19092}

# Function to create topic if it doesn't exist
create_topic() {
    local topic=$1
    local partitions=${2:-3}
    local replicas=${3:-1}

    echo "  Creating topic: $topic (partitions=$partitions, replicas=$replicas)"
    docker exec redpanda rpk topic create "$topic" \
        --brokers "$KAFKA_BROKER" \
        --partitions "$partitions" \
        --replicas "$replicas" 2>/dev/null || echo "    Topic $topic already exists"
}

# Core product and inventory topics
echo "📦 Creating product and inventory topics..."
create_topic "products" 3 1
create_topic "product-updates" 3 1
create_topic "inventory-events" 3 1

# WebSocket and processing topics
echo "🔌 Creating WebSocket and processing topics..."
create_topic "websocket_fanout" 3 1
create_topic "processing_fanout" 3 1

# E-commerce event topics
echo "🛒 Creating e-commerce topics..."
create_topic "ecommerce-events" 3 1
create_topic "ecommerce_processing_fanout" 3 1
create_topic "shopping-cart-events" 3 1
create_topic "order-events" 3 1

# Recommendation and basket analysis topics
echo "🎯 Creating recommendation topics..."
create_topic "product-recommendations" 3 1
create_topic "basket-patterns" 3 1

# Shopping assistant topics
echo "💬 Creating shopping assistant topics..."
create_topic "shopping-assistant-chat" 3 1
create_topic "assistant-responses" 3 1

echo ""
echo "✅ All Kafka topics created successfully!"
echo ""
echo "📋 Topic List:"
docker exec redpanda rpk topic list --brokers "$KAFKA_BROKER"
