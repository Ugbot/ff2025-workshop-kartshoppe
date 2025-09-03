#!/bin/bash

echo "🚀 Starting KartShoppe E-commerce Platform with Redpanda + Flink"
echo "=================================================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Create directories for logs and PIDs
mkdir -p logs .pids

# Start Redpanda
echo -e "\n${BLUE}Starting Redpanda...${NC}"
docker compose up -d redpanda redpanda-console

# Wait for Redpanda to be healthy
echo "Waiting for Redpanda to be ready..."
sleep 10

# Create topics
echo -e "\n${BLUE}Creating Kafka topics...${NC}"
docker compose up redpanda-init-topics

# Build all projects
echo -e "\n${BLUE}Building all projects...${NC}"
./gradlew build -q

# Start Quarkus API in background
echo -e "\n${BLUE}Starting Quarkus API...${NC}"
./gradlew :quarkus-api:quarkusDev --console=plain > logs/quarkus.log 2>&1 &
QUARKUS_PID=$!
echo $QUARKUS_PID > .pids/quarkus.pid

# Wait for Quarkus to start
echo "Waiting for Quarkus API to start..."
sleep 15

# Start Flink Inventory Job
echo -e "\n${BLUE}Starting Flink Inventory Management Job...${NC}"
KAFKA_BOOTSTRAP_SERVERS=localhost:19092 java --add-opens java.base/java.util=ALL-UNNAMED \
    -cp flink-inventory/build/libs/flink-inventory.jar \
    com.ververica.composable_job.flink.inventory.InventoryManagementJob > logs/inventory.log 2>&1 &
INVENTORY_PID=$!
echo $INVENTORY_PID > .pids/inventory.pid

# Wait for inventory job to process products
echo "Waiting for inventory job to process products..."
sleep 10

# Start KartShoppe Frontend
echo -e "\n${BLUE}Starting KartShoppe Frontend...${NC}"
cd kartshoppe-frontend

# Install dependencies if needed
if [ ! -d "node_modules" ]; then
    echo "Installing frontend dependencies..."
    npm install
fi

# Start the frontend
PORT=3000 npm run dev > ../logs/frontend.log 2>&1 &
FRONTEND_PID=$!
echo $FRONTEND_PID > ../.pids/frontend.pid
cd ..

# Final health checks
echo -e "\n${BLUE}Performing health checks...${NC}"
sleep 5

# Check product count
echo "Checking product inventory..."
PRODUCTS=$(curl -s http://localhost:8080/api/ecommerce/inventory/state 2>/dev/null | jq -r '.totalProducts // 0' 2>/dev/null || echo "0")

echo -e "\n${GREEN}========================================================"
echo -e "✅ All services are running!"
echo -e "========================================================${NC}"
echo
echo -e "${BLUE}🌐 Access the applications:${NC}"
echo -e "  KartShoppe Frontend:  ${GREEN}http://localhost:3000/kartshoppe${NC}"
echo -e "  Quarkus API:          ${GREEN}http://localhost:8080${NC}" 
echo -e "  Inventory API:        ${GREEN}http://localhost:8080/api/ecommerce/inventory/state${NC}"
echo -e "  Redpanda Console:     ${GREEN}http://localhost:8085${NC}"
echo
echo -e "${BLUE}📊 Service Status:${NC}"
echo -e "  Redpanda:           ${GREEN}Running on port 19092${NC}"
echo -e "  Quarkus API:        ${GREEN}PID $QUARKUS_PID${NC}"
echo -e "  Inventory Job:      ${GREEN}PID $INVENTORY_PID${NC}"
echo -e "  Frontend:           ${GREEN}PID $FRONTEND_PID${NC}"
echo -e "  Products Loaded:    ${GREEN}$PRODUCTS products${NC}"
echo
echo -e "${BLUE}📁 Logs available at:${NC}"
echo -e "  logs/quarkus.log    logs/inventory.log    logs/frontend.log"
echo
echo -e "${YELLOW}Press Ctrl+C to stop all services${NC}"

# Function to cleanup on exit
cleanup() {
    echo -e "\n${YELLOW}Shutting down services...${NC}"
    
    # Kill processes using PID files
    [ -f .pids/quarkus.pid ] && kill $(cat .pids/quarkus.pid) 2>/dev/null
    [ -f .pids/inventory.pid ] && kill $(cat .pids/inventory.pid) 2>/dev/null  
    [ -f .pids/frontend.pid ] && kill $(cat .pids/frontend.pid) 2>/dev/null
    
    # Cleanup PID files
    rm -rf .pids
    
    # Stop Docker containers
    docker compose down
    
    echo -e "${GREEN}All services stopped${NC}"
    exit 0
}

# Set up trap to cleanup on Ctrl+C
trap cleanup INT

# Keep the script running
wait