#!/bin/bash

echo "🛒 Starting Flink Basket Analysis & Recommendation Job"
echo "========================================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check prerequisites
echo -e "\n${BLUE}Checking prerequisites...${NC}"

# Check if Redpanda is running
if ! lsof -Pi :19092 -sTCP:LISTEN -t >/dev/null ; then
    echo -e "${YELLOW}⚠ Redpanda is not running on port 19092${NC}"
    echo "Starting Redpanda..."
    docker compose up -d redpanda redpanda-console redpanda-init-topics
    echo "Waiting for Redpanda to start..."
    sleep 15
else
    echo -e "${GREEN}✓ Redpanda is running${NC}"
fi

# Verify Kafka topics exist
echo -e "\n${BLUE}Verifying Kafka topics...${NC}"
REQUIRED_TOPICS=(
    "ecommerce-events"
    "shopping-cart-events"
    "product-recommendations"
    "basket-patterns"
    "websocket_fanout"
)

echo "Creating topics if missing..."
docker compose up redpanda-init-topics

for topic in "${REQUIRED_TOPICS[@]}"; do
    echo -e "${GREEN}✓${NC} Topic '$topic' ready"
done

# Build the Flink job
echo -e "\n${BLUE}Building Flink recommendations job...${NC}"
./gradlew :flink-recommendations:build -x buildDeepNetts -x test

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Build successful${NC}"
else
    echo -e "${RED}✗ Build failed${NC}"
    echo "Try running: ./gradlew :flink-recommendations:compileJava -x buildDeepNetts"
    exit 1
fi

# Run the Flink job
echo -e "\n${BLUE}Starting Basket Analysis Job (BasketAnalysisJobRefactored)...${NC}"
echo -e "${YELLOW}This job will:${NC}"
echo "  1. Track shopping baskets (Keyed State + Timers)"
echo "  2. Mine association rules from completed baskets"
echo "  3. Generate recommendations using broadcast state"
echo "  4. Output to product-recommendations topic"
echo ""

# Set environment variables
export KAFKA_BOOTSTRAP_SERVERS=localhost:19092
export PARALLELISM=2
export CHECKPOINT_INTERVAL_MS=30000
export PAIMON_WAREHOUSE="${PAIMON_WAREHOUSE:-/tmp/paimon}"

echo -e "${BLUE}Environment:${NC}"
echo "  KAFKA_BOOTSTRAP_SERVERS=$KAFKA_BOOTSTRAP_SERVERS"
echo "  PARALLELISM=$PARALLELISM"
echo "  CHECKPOINT_INTERVAL=$CHECKPOINT_INTERVAL_MS"
echo "  PAIMON_WAREHOUSE=$PAIMON_WAREHOUSE (optional - for ML training data)"
echo ""

# Run with gradle
echo -e "${GREEN}🚀 Starting Flink job...${NC}"
echo -e "${YELLOW}Press Ctrl+C to stop${NC}"
echo "=========================================================="
echo ""

./gradlew :flink-recommendations:run

# Cleanup on exit
echo -e "\n${YELLOW}Job stopped${NC}"
