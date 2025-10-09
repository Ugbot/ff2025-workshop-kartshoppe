#!/bin/bash

# ==============================================================================
# Script 1: Start Infrastructure (Redpanda, PostgreSQL)
# ==============================================================================

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  Step 1: Starting Infrastructure Services               ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════╝${NC}"
echo ""

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}✗ Docker is not running. Please start Docker Desktop first.${NC}"
    exit 1
fi

echo -e "${GREEN}✓${NC} Docker is running"
echo ""

# Clean up any existing containers
echo -e "${YELLOW}Cleaning up existing containers...${NC}"
docker compose down 2>/dev/null || true

# Start infrastructure services
echo -e "${BLUE}Starting Redpanda (Kafka), PostgreSQL, and Redpanda Console...${NC}"
docker compose up -d redpanda postgres redpanda-console

# Wait for Redpanda to be healthy
echo -e "${YELLOW}Waiting for Redpanda to be healthy...${NC}"
timeout 60 bash -c 'until docker compose ps redpanda | grep -q "healthy"; do sleep 2; echo -n "."; done' || {
    echo -e "\n${RED}✗ Redpanda failed to start${NC}"
    exit 1
}
echo ""
echo -e "${GREEN}✓${NC} Redpanda is healthy"

# Wait for PostgreSQL to be healthy
echo -e "${YELLOW}Waiting for PostgreSQL to be healthy...${NC}"
timeout 30 bash -c 'until docker compose ps postgres | grep -q "healthy"; do sleep 2; echo -n "."; done' || {
    echo -e "\n${RED}✗ PostgreSQL failed to start${NC}"
    exit 1
}
echo ""
echo -e "${GREEN}✓${NC} PostgreSQL is healthy"

# Create Kafka topics
echo -e "${BLUE}Creating Kafka topics...${NC}"
docker compose up redpanda-init-topics
echo -e "${GREEN}✓${NC} Kafka topics created"

echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  Infrastructure Ready!                                   ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${BLUE}Services Running:${NC}"
echo -e "  ${GREEN}✓${NC} Redpanda (Kafka):        localhost:19092"
echo -e "  ${GREEN}✓${NC} Redpanda Console:        http://localhost:8085"
echo -e "  ${GREEN}✓${NC} PostgreSQL:              localhost:5432"
echo ""
echo -e "${YELLOW}Next step:${NC} Run ${GREEN}./2-start-quarkus.sh${NC}"
echo ""
