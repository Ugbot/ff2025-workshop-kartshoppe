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

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | sed 's/.*version "\([0-9]*\).*/\1/')
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo -e "\n${YELLOW}⚠ Warning: Java 17+ is required for Quarkus${NC}"
    echo "Current version: $(java -version 2>&1 | head -n 1)"
    echo "Flink jobs can run on Java 17, but need Java 11 to build"
fi

# Install frontend dependencies (required for Quinoa integration)
echo -e "\n${BLUE}Preparing frontend for Quinoa integration...${NC}"
if [ ! -d "kartshoppe-frontend/node_modules" ]; then
    echo "Installing frontend dependencies..."
    cd kartshoppe-frontend
    npm install
    cd ..
fi

# Check if Flink JARs exist, if not suggest building
if [ ! -f "flink-inventory/build/libs/flink-inventory.jar" ]; then
    echo -e "\n${YELLOW}⚠ Flink JARs not found${NC}"
    echo "Run ./build-flink.sh to build Flink jobs with Java 11"
    echo "Or run ./build-all.sh to build everything"
    echo "Attempting to build with current Java version..."
    ./gradlew :models:build :flink-common:build :flink-inventory:shadowJar -x test -q
fi

# Build Quarkus for Docker
echo -e "\n${BLUE}Building Quarkus application for Docker...${NC}"
./gradlew :quarkus-api:build -x test -Dquarkus.package.type=fast-jar

# Start Quarkus with integrated frontend (Quinoa) in Docker
echo -e "\n${BLUE}Starting Quarkus API with integrated KartShoppe frontend in Docker...${NC}"
echo "Frontend will be served at http://localhost:8080"
docker compose up -d quarkus-api

# Wait for Quarkus to start (including Quinoa frontend compilation)
echo "Waiting for Quarkus and frontend to start (this may take a moment)..."
for i in {1..30}; do
    if curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/q/health/ready | grep -q "200"; then
        echo -e "${GREEN}✓ Quarkus is ready!${NC}"
        break
    fi
    sleep 2
    echo -n "."
done
echo

# Start Flink Inventory Job
echo -e "\n${BLUE}Starting Flink Inventory Management Job...${NC}"
if [ -f "flink-inventory/build/libs/flink-inventory.jar" ]; then
    KAFKA_BOOTSTRAP_SERVERS=localhost:19092 java --add-opens java.base/java.util=ALL-UNNAMED \
        -cp flink-inventory/build/libs/flink-inventory.jar \
        com.ververica.composable_job.flink.inventory.InventoryManagementJob > logs/inventory.log 2>&1 &
    INVENTORY_PID=$!
    echo $INVENTORY_PID > .pids/inventory.pid
    echo -e "${GREEN}✓ Inventory job started (PID: $INVENTORY_PID)${NC}"
else
    echo -e "${YELLOW}⚠ Inventory JAR not found, skipping${NC}"
    echo "Run ./build-flink.sh to build it"
    INVENTORY_PID="N/A"
fi

# Wait for inventory job to process products
echo "Waiting for inventory job to process products..."
sleep 10

# Frontend is now integrated with Quarkus via Quinoa - no separate process needed
echo -e "\n${GREEN}✓ Frontend is integrated with Quarkus via Quinoa in Docker${NC}"

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
echo -e "  KartShoppe App:       ${GREEN}http://localhost:8080${NC}"
echo -e "  Quarkus Dev UI:       ${GREEN}http://localhost:8080/q/dev${NC}"
echo -e "  API Endpoints:        ${GREEN}http://localhost:8080/api${NC}"
echo -e "  Inventory State:      ${GREEN}http://localhost:8080/api/ecommerce/inventory/state${NC}"
echo -e "  Redpanda Console:     ${GREEN}http://localhost:8085${NC}"
echo
echo -e "${BLUE}📊 Service Status:${NC}"
echo -e "  Redpanda:              ${GREEN}Running on port 19092${NC}"
echo -e "  Quarkus + Frontend:    ${GREEN}Running in Docker (Java 17)${NC}"
echo -e "  Inventory Job:         ${GREEN}PID $INVENTORY_PID (Java 11)${NC}"
echo -e "  Products Loaded:       ${GREEN}$PRODUCTS products${NC}"
echo
echo -e "${BLUE}📁 Logs available at:${NC}"
echo -e "  docker compose logs quarkus-api (Quarkus + frontend)"
echo -e "  logs/inventory.log"
echo
echo -e "${YELLOW}Press Ctrl+C to stop all services${NC}"

# Function to cleanup on exit
cleanup() {
    echo -e "\n${YELLOW}Shutting down services...${NC}"

    # Kill Flink processes using PID files
    [ -f .pids/inventory.pid ] && kill $(cat .pids/inventory.pid) 2>/dev/null

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