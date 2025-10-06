#!/bin/bash

echo "🧪 Testing Basket Analysis → Recommendations Pipeline"
echo "======================================================"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Test step counter
STEP=1

test_step() {
    echo -e "\n${BLUE}Step $STEP: $1${NC}"
    STEP=$((STEP + 1))
}

check_service() {
    if lsof -Pi :$2 -sTCP:LISTEN -t >/dev/null 2>&1 ; then
        echo -e "${GREEN}✓${NC} $1 is running on port $2"
        return 0
    else
        echo -e "${RED}✗${NC} $1 is not running on port $2"
        return 1
    fi
}

# Test 1: Check Redpanda
test_step "Check Redpanda (Kafka)"
if check_service "Redpanda" 19092; then
    echo "Listing topics..."
    docker exec redpanda rpk topic list 2>/dev/null | grep -E "(ecommerce-events|product-recommendations|basket-patterns)" || true
else
    echo -e "${YELLOW}Starting Redpanda...${NC}"
    docker compose up -d redpanda redpanda-console redpanda-init-topics
    sleep 15
fi

# Test 2: Check Quarkus API
test_step "Check Quarkus API + Frontend"
if check_service "Quarkus API" 8080; then
    echo "Testing health endpoint..."
    if curl -s http://localhost:8080/q/health/ready | grep -q "UP"; then
        echo -e "${GREEN}✓${NC} Quarkus is healthy"
    else
        echo -e "${YELLOW}⚠${NC} Quarkus may not be ready yet"
    fi
else
    echo -e "${YELLOW}Quarkus is not running. Start it with:${NC}"
    echo "  ./start-kartshoppe.sh"
    echo ""
    echo -e "${YELLOW}Continuing without Quarkus for now...${NC}"
fi

# Test 3: Check Flink Job
test_step "Check Flink Basket Job"
if ps aux | grep -q "[B]asketAnalysisJobRefactored"; then
    echo -e "${GREEN}✓${NC} Flink basket job is running"
    echo "Recent log entries:"
    ps aux | grep "[B]asketAnalysisJobRefactored" | head -1
else
    echo -e "${YELLOW}✗${NC} Flink basket job is not running"
    echo "Start it with: ./start-basket-job.sh"
fi

# Test 4: Create Kafka topics if missing
test_step "Verify Kafka Topics"
REQUIRED_TOPICS=(
    "ecommerce-events"
    "shopping-cart-events"
    "product-recommendations"
    "basket-patterns"
    "websocket_fanout"
)

echo "Ensuring topics exist..."
docker compose up redpanda-init-topics 2>/dev/null

echo "Checking topics..."
for topic in "${REQUIRED_TOPICS[@]}"; do
    if docker exec redpanda rpk topic list 2>/dev/null | grep -q "^$topic"; then
        echo -e "${GREEN}✓${NC} $topic"
    else
        echo -e "${YELLOW}⚠${NC} $topic (may need manual creation)"
    fi
done

# Test 5: Test Event Flow
test_step "Test Event Publishing (Optional)"
echo -e "${BLUE}Would you like to publish a test shopping event? (y/n)${NC}"
read -t 5 -n 1 -r REPLY || REPLY='n'
echo ""

if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "Publishing test ADD_TO_CART event..."

    TEST_EVENT=$(cat <<EOF
{
  "eventId": "test_${RANDOM}",
  "sessionId": "test_session_123",
  "userId": "test_user_456",
  "eventType": "ADD_TO_CART",
  "timestamp": $(date +%s)000,
  "productId": "prod_laptop_001",
  "productName": "Gaming Laptop",
  "categoryId": "electronics",
  "value": 1200.0,
  "quantity": 1
}
EOF
)

    echo "$TEST_EVENT" | docker exec -i redpanda rpk topic produce ecommerce-events
    echo -e "${GREEN}✓${NC} Test event published to ecommerce-events"

    echo "Waiting 2 seconds for Flink to process..."
    sleep 2

    echo "Checking for recommendations (last 3 messages)..."
    docker exec redpanda rpk topic consume product-recommendations --num 3 2>/dev/null || echo "No messages yet"
else
    echo "Skipping test event"
fi

# Test 6: Summary
test_step "Summary & Next Steps"
echo ""
echo -e "${BLUE}=== System Status ===${NC}"
check_service "Redpanda" 19092 && REDPANDA_OK=1 || REDPANDA_OK=0
check_service "Quarkus" 8080 && QUARKUS_OK=1 || QUARKUS_OK=0
ps aux | grep -q "[B]asketAnalysisJobRefactored" && FLINK_OK=1 || FLINK_OK=0

echo ""
echo -e "${BLUE}=== To Start Testing ===${NC}"

if [ $REDPANDA_OK -eq 0 ]; then
    echo "1. Start Redpanda:"
    echo "   docker compose up -d redpanda redpanda-console"
fi

if [ $QUARKUS_OK -eq 0 ]; then
    echo "2. Start Quarkus + Frontend:"
    echo "   ./start-kartshoppe.sh"
fi

if [ $FLINK_OK -eq 0 ]; then
    echo "3. Start Flink Basket Job:"
    echo "   ./start-basket-job.sh"
fi

if [ $QUARKUS_OK -eq 1 ]; then
    echo ""
    echo -e "${GREEN}=== Ready to Test! ===${NC}"
    echo "4. Open browser: http://localhost:8080"
    echo "5. Add items to cart (e.g., laptop, mouse, keyboard)"
    echo "6. Watch for recommendations with '🤖 AI Basket Analysis' badge"
    echo "7. Check confidence scores from pattern mining"
fi

echo ""
echo -e "${BLUE}=== Monitoring Commands ===${NC}"
echo "Watch recommendations:"
echo "  docker exec -it redpanda rpk topic consume product-recommendations"
echo ""
echo "Watch basket patterns:"
echo "  docker exec -it redpanda rpk topic consume basket-patterns"
echo ""
echo "Watch raw events:"
echo "  docker exec -it redpanda rpk topic consume ecommerce-events"
echo ""
echo "See full demo guide:"
echo "  cat BASKET-RECOMMENDATIONS-DEMO.md"
echo ""
