#!/bin/bash

echo "🛒 Starting Flink Basket Analysis Job WITH PRE-TRAINING"
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
docker compose up redpanda-init-topics 2>/dev/null

for topic in "${REQUIRED_TOPICS[@]}"; do
    echo -e "${GREEN}✓${NC} Topic '$topic' ready"
done

# Check for Paimon training data
echo -e "\n${BLUE}Checking Paimon training data...${NC}"
PAIMON_WAREHOUSE="${PAIMON_WAREHOUSE:-/tmp/paimon}"

if [ -d "$PAIMON_WAREHOUSE/basket_analysis.db/basket_patterns" ]; then
    PATTERN_COUNT=$(find "$PAIMON_WAREHOUSE/basket_analysis.db/basket_patterns" -name "*.orc" -o -name "*.parquet" | wc -l | tr -d ' ')
    echo -e "${GREEN}✓ Found Paimon training data${NC}"
    echo "  Warehouse: $PAIMON_WAREHOUSE"
    echo "  Database: basket_analysis"
    echo "  Table: basket_patterns"
    echo "  Data files: $PATTERN_COUNT"
    echo ""
    echo -e "${GREEN}🎯 PRE-TRAINING ENABLED${NC}"
    echo "  Job will load historical patterns before processing events"
else
    echo -e "${YELLOW}⚠ No Paimon training data found${NC}"
    echo "  Warehouse checked: $PAIMON_WAREHOUSE"
    echo ""
    echo -e "${YELLOW}Would you like to generate training data now? (y/n)${NC}"
    read -t 10 -n 1 -r REPLY || REPLY='n'
    echo ""

    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${BLUE}Generating training data...${NC}"
        ./init-paimon-training-data.sh
        if [ $? -ne 0 ]; then
            echo -e "${RED}✗ Failed to generate training data${NC}"
            echo "  Job will run without pre-training (cold start)"
        fi
    else
        echo -e "${YELLOW}Skipping training data generation${NC}"
        echo "  Job will run without pre-training (cold start)"
        echo "  Run ./init-paimon-training-data.sh later to enable pre-training"
    fi
fi

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

# Run the Flink job with pre-training
echo -e "\n${BLUE}Starting Basket Analysis Job (WITH PRE-TRAINING)${NC}"
echo -e "${YELLOW}This job will:${NC}"
echo "  1. Load historical patterns from Paimon (if available)"
echo "  2. Track shopping baskets (Keyed State + Timers)"
echo "  3. Mine association rules from completed baskets"
echo "  4. Generate recommendations using broadcast state"
echo "  5. Output to product-recommendations topic"
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
echo "  PAIMON_WAREHOUSE=$PAIMON_WAREHOUSE"
echo ""

# Display mode
if [ -d "$PAIMON_WAREHOUSE/basket_analysis.db/basket_patterns" ]; then
    echo -e "${GREEN}🚀 MODE: WARM START (with historical patterns)${NC}"
    echo "   Historical patterns will be loaded from Paimon"
    echo "   Recommendations will work from first shopping event!"
else
    echo -e "${YELLOW}🚀 MODE: COLD START (no historical patterns)${NC}"
    echo "   Job will build patterns from live traffic only"
    echo "   Run ./init-paimon-training-data.sh to enable warm start"
fi

# Run with gradle (using the pre-training job)
echo ""
echo -e "${GREEN}🚀 Starting Flink job...${NC}"
echo -e "${YELLOW}Press Ctrl+C to stop${NC}"
echo "==========================================================="
echo ""

./gradlew :flink-recommendations:run -PmainClass=com.ververica.composable_job.flink.recommendations.BasketAnalysisJobWithPretraining

# Cleanup on exit
echo -e "\n${YELLOW}Job stopped${NC}"
