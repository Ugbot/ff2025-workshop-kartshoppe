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

# Start PostgreSQL first (required for order persistence and CDC)
echo -e "${YELLOW}Starting PostgreSQL (for order persistence & CDC)...${NC}"
docker compose up -d postgres

# Wait for PostgreSQL to be healthy
echo -e "${YELLOW}Waiting for PostgreSQL to be healthy...${NC}"
timeout 30 bash -c 'until docker compose ps postgres | grep -q "healthy"; do sleep 2; echo -n "."; done' || {
    echo -e "\n${RED}✗ PostgreSQL failed to start${NC}"
    exit 1
}
echo ""
echo -e "${GREEN}✓${NC} PostgreSQL is healthy"

# Initialize PostgreSQL schema (only if not already initialized)
echo -e "${YELLOW}Initializing PostgreSQL schema...${NC}"
if docker exec postgres-cdc psql -U postgres -d ecommerce -c "SELECT 1 FROM orders LIMIT 1;" > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} PostgreSQL schema already initialized"
else
    echo -e "${YELLOW}  Running postgres-init.sql via docker exec...${NC}"
    cat postgres-init.sql | docker exec -i postgres-cdc psql -U postgres -d ecommerce > /dev/null 2>&1
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓${NC} PostgreSQL schema initialized successfully"
    else
        echo -e "${RED}✗ Failed to initialize PostgreSQL schema${NC}"
        exit 1
    fi
fi

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
    "order-events"
)

# Create topics using docker compose
docker compose up redpanda-init-topics
for topic in "${KAFKA_TOPICS[@]}"; do
    echo -e "${GREEN}✓${NC} Topic '$topic' created"
done

# Install frontend dependencies (required for Quinoa)
if [ ! -d "kartshoppe-frontend/node_modules" ]; then
    echo -e "\n${BLUE}Installing frontend dependencies...${NC}"
    cd kartshoppe-frontend && npm install && cd ..
fi

# Build the models module
echo -e "\n${BLUE}Building required modules...${NC}"
./gradlew :models:build -q

# Start Quarkus with integrated frontend (Quinoa)
echo -e "\n${BLUE}Starting Quarkus API with integrated KartShoppe frontend...${NC}"
echo "Frontend will be served at http://localhost:8080"
./gradlew :quarkus-api:quarkusDev &
QUARKUS_PID=$!

# Wait for Quarkus to start
echo "Waiting for Quarkus and frontend to start..."
for i in {1..30}; do
    if curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/q/health/ready | grep -q "200"; then
        echo -e "\n${GREEN}✓ Quarkus with integrated frontend is ready!${NC}"
        break
    fi
    sleep 2
    echo -n "."
done

echo -e "\n${GREEN}=========================================="
echo -e "🎉 KartShoppe is starting up!"
echo -e "==========================================${NC}"
echo
echo -e "${BLUE}Access the application at:${NC}"
echo -e "  KartShoppe App:  ${GREEN}http://localhost:8080${NC}"
echo -e "  Quarkus Dev UI:  ${GREEN}http://localhost:8080/q/dev${NC}"
echo -e "  API Endpoints:   ${GREEN}http://localhost:8080/api/ecommerce${NC}"
echo -e "  WebSocket:       ${GREEN}ws://localhost:8080/ecommerce/{sessionId}/{userId}${NC}"
echo
echo -e "${BLUE}Services Running:${NC}"
echo -e "  ${GREEN}✓${NC} PostgreSQL:     Port 5432"
echo -e "  ${GREEN}✓${NC} Redpanda:       Port 19092"
echo -e "  ${GREEN}✓${NC} Quarkus API:    Port 8080"
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
    echo -e "${GREEN}Services stopped${NC}"
    exit 0
}

# Set up trap to cleanup on Ctrl+C
trap cleanup INT

# Keep the script running
wait