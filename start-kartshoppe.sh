#!/bin/bash

echo "🛒 Starting KartShoppe E-commerce Platform"
echo "=========================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to check if a service is running
check_service() {
    if lsof -Pi :$2 -sTCP:LISTEN -t >/dev/null ; then
        echo -e "${GREEN}✓${NC} $1 is running on port $2"
        return 0
    else
        echo -e "${YELLOW}⚠${NC} $1 is not running on port $2"
        return 1
    fi
}

# Check prerequisites
echo -e "\n${BLUE}Checking prerequisites...${NC}"
check_service "Redpanda" 19092
REDPANDA_RUNNING=$?

if [ $REDPANDA_RUNNING -ne 0 ]; then
    echo -e "\n${YELLOW}Starting Redpanda...${NC}"
    if [ -f "docker-compose.yml" ]; then
        docker compose up -d redpanda redpanda-console
        echo "Waiting for Redpanda to start..."
        sleep 10
    else
        echo -e "${RED}docker-compose.yml not found. Please start Redpanda manually.${NC}"
    fi
fi

# Create Kafka topics for e-commerce
echo -e "\n${BLUE}Creating Kafka topics...${NC}"
KAFKA_TOPICS=(
    "ecommerce_events"
    "ecommerce_processing_fanout"
    "product_updates"
    "recommendations"
    "inventory_updates"
)

# Create topics using docker compose
docker compose up redpanda-init-topics
for topic in "${KAFKA_TOPICS[@]}"; do
    echo -e "${GREEN}✓${NC} Topic '$topic' created"
done

# Build the models module first
echo -e "\n${BLUE}Building models module...${NC}"
./gradlew :models:build

# Start Quarkus API in development mode
echo -e "\n${BLUE}Starting Quarkus API...${NC}"
./gradlew :quarkus-api:quarkusDev &
QUARKUS_PID=$!

# Wait for Quarkus to start
echo "Waiting for Quarkus to start..."
sleep 15
check_service "Quarkus API" 8080

# Start KartShoppe Frontend
echo -e "\n${BLUE}Starting KartShoppe Frontend...${NC}"
cd kartshoppe-frontend

# Install dependencies if needed
if [ ! -d "node_modules" ]; then
    echo "Installing frontend dependencies..."
    npm install
fi

# Start the frontend
npm run dev &
FRONTEND_PID=$!
cd ..

echo -e "\n${GREEN}=========================================="
echo -e "🎉 KartShoppe is starting up!"
echo -e "==========================================${NC}"
echo
echo -e "${BLUE}Access the application at:${NC}"
echo -e "  Frontend:    ${GREEN}http://localhost:3000/kartshoppe${NC}"
echo -e "  API:         ${GREEN}http://localhost:8080/api/ecommerce${NC}"
echo -e "  WebSocket:   ${GREEN}ws://localhost:8080/ecommerce/{sessionId}/{userId}${NC}"
echo
echo -e "${BLUE}Kafka Topics:${NC}"
for topic in "${KAFKA_TOPICS[@]}"; do
    echo -e "  - $topic"
done
echo
echo -e "${YELLOW}Press Ctrl+C to stop all services${NC}"

# Function to cleanup on exit
cleanup() {
    echo -e "\n${YELLOW}Shutting down services...${NC}"
    kill $QUARKUS_PID 2>/dev/null
    kill $FRONTEND_PID 2>/dev/null
    echo -e "${GREEN}Services stopped${NC}"
    exit 0
}

# Set up trap to cleanup on Ctrl+C
trap cleanup INT

# Keep the script running
wait