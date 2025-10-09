#!/bin/bash

# ==============================================================================
# Script 2: Start Quarkus API
# ==============================================================================

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  Step 2: Starting Quarkus API                            ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════╝${NC}"
echo ""

# Check if Redpanda is running
if ! lsof -Pi :19092 -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo -e "${RED}✗ Redpanda is not running on port 19092${NC}"
    echo -e "${YELLOW}  Please run ${GREEN}./1-start-infrastructure.sh${YELLOW} first${NC}"
    exit 1
fi

echo -e "${GREEN}✓${NC} Redpanda is running"
echo ""

# Check if port 8080 is already in use
if lsof -Pi :8080 -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo -e "${YELLOW}⚠ Port 8080 is already in use${NC}"
    echo -e "${YELLOW}  Killing existing process...${NC}"
    lsof -ti:8080 | xargs kill -9 2>/dev/null || true
    sleep 2
fi

# Build the models module first
echo -e "${BLUE}Building required modules...${NC}"
./gradlew :models:build -q
echo -e "${GREEN}✓${NC} Models module built"
echo ""

# Create logs directory
mkdir -p logs

# Start Quarkus in dev mode
echo -e "${BLUE}Starting Quarkus API...${NC}"
echo -e "${YELLOW}This will run in the foreground. Press Ctrl+C to stop.${NC}"
echo ""

# Save PID for cleanup
echo $$ > .pids/quarkus.pid

./gradlew :quarkus-api:quarkusDev
