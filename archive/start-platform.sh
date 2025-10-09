#!/bin/bash

# ==============================================================================
# Master Script: Start KartShoppe Platform (All-in-One)
# ==============================================================================
# This script starts all services in the correct order in separate terminal panes
# or provides instructions for manual startup.
# ==============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}"
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║                                                                ║"
echo "║        🛒  KartShoppe E-Commerce Platform Startup  🛒          ║"
echo "║                                                                ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo -e "${NC}"
echo ""
echo -e "${YELLOW}This will start the KartShoppe platform in 3 steps:${NC}"
echo ""
echo -e "  ${BLUE}1.${NC} Infrastructure (Redpanda/Kafka, PostgreSQL)"
echo -e "  ${BLUE}2.${NC} Quarkus API (Backend)"
echo -e "  ${BLUE}3.${NC} React Frontend"
echo ""
echo -e "${YELLOW}Note:${NC} Flink jobs are started separately as needed"
echo ""

# Create .pids directory
mkdir -p .pids

# Step 1: Start Infrastructure
echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}Step 1/3: Starting Infrastructure${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
echo ""

./1-start-infrastructure.sh

echo ""
read -p "Press ENTER to continue to Step 2 (Quarkus API)..."
echo ""

# Step 2: Start Quarkus
echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}Step 2/3: Starting Quarkus API${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${YELLOW}Opening Quarkus in a new terminal window...${NC}"
echo ""

# Detect terminal type and open new window
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS - use osascript to open new Terminal window
    osascript <<EOF
tell application "Terminal"
    do script "cd \"$(pwd)\" && ./2-start-quarkus.sh"
    activate
end tell
EOF
    echo -e "${GREEN}✓${NC} Quarkus starting in new Terminal window"
elif command -v gnome-terminal > /dev/null 2>&1; then
    # Linux with GNOME Terminal
    gnome-terminal -- bash -c "cd $(pwd) && ./2-start-quarkus.sh; exec bash"
    echo -e "${GREEN}✓${NC} Quarkus starting in new terminal"
else
    echo -e "${YELLOW}⚠ Could not detect terminal type${NC}"
    echo -e "${YELLOW}  Please open a NEW terminal and run:${NC}"
    echo -e "  ${GREEN}./2-start-quarkus.sh${NC}"
    echo ""
fi

echo ""
echo -e "${YELLOW}Waiting for Quarkus to be ready...${NC}"
for i in {1..40}; do
    if curl -s http://localhost:8080/q/health/ready > /dev/null 2>&1; then
        echo ""
        echo -e "${GREEN}✓${NC} Quarkus API is ready!"
        break
    fi
    sleep 3
    echo -n "."
done

echo ""
read -p "Press ENTER to continue to Step 3 (Frontend)..."
echo ""

# Step 3: Start Frontend
echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}Step 3/3: Starting Frontend${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${YELLOW}Opening Frontend in a new terminal window...${NC}"
echo ""

if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    osascript <<EOF
tell application "Terminal"
    do script "cd \"$(pwd)\" && ./3-start-frontend.sh"
    activate
end tell
EOF
    echo -e "${GREEN}✓${NC} Frontend starting in new Terminal window"
elif command -v gnome-terminal > /dev/null 2>&1; then
    # Linux with GNOME Terminal
    gnome-terminal -- bash -c "cd $(pwd) && ./3-start-frontend.sh; exec bash"
    echo -e "${GREEN}✓${NC} Frontend starting in new terminal"
else
    echo -e "${YELLOW}⚠ Could not detect terminal type${NC}"
    echo -e "${YELLOW}  Please open a NEW terminal and run:${NC}"
    echo -e "  ${GREEN}./3-start-frontend.sh${NC}"
    echo ""
fi

echo ""
echo -e "${YELLOW}Waiting for frontend to be ready...${NC}"
for i in {1..30}; do
    if curl -s http://localhost:3000 > /dev/null 2>&1; then
        echo ""
        echo -e "${GREEN}✓${NC} Frontend is ready!"
        break
    fi
    sleep 2
    echo -n "."
done

echo ""
echo -e "${GREEN}"
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║                                                                ║"
echo "║              ✨  Platform Started Successfully!  ✨            ║"
echo "║                                                                ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo -e "${NC}"
echo ""
echo -e "${BLUE}Access Points:${NC}"
echo -e "  ${GREEN}▸${NC} KartShoppe App:       ${GREEN}http://localhost:3000${NC}"
echo -e "  ${GREEN}▸${NC} Quarkus Dev UI:       ${GREEN}http://localhost:8080/q/dev${NC}"
echo -e "  ${GREEN}▸${NC} Redpanda Console:     ${GREEN}http://localhost:8085${NC}"
echo -e "  ${GREEN}▸${NC} API Endpoints:        ${GREEN}http://localhost:8080/api/*${NC}"
echo ""
echo -e "${BLUE}Next Steps (Optional - Run Flink Jobs):${NC}"
echo -e "  ${YELLOW}▸${NC} Inventory Management:  ${GREEN}./start-inventory.sh${NC}"
echo -e "  ${YELLOW}▸${NC} Basket Analysis:       ${GREEN}./start-basket-job.sh${NC}"
echo -e "  ${YELLOW}▸${NC} Shopping Assistant:    ${GREEN}./start-shopping-assistant.sh${NC}"
echo ""
echo -e "${BLUE}To Stop Everything:${NC}"
echo -e "  ${GREEN}./stop-platform.sh${NC}"
echo ""
