#!/bin/bash

echo "🛑 Stopping KartShoppe E-commerce Platform"
echo "============================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to kill process by PID file
kill_by_pid_file() {
    local pidfile=$1
    local service_name=$2
    
    if [ -f "$pidfile" ]; then
        local pid=$(cat "$pidfile")
        if kill "$pid" 2>/dev/null; then
            echo -e "${GREEN}✓${NC} Stopped $service_name (PID: $pid)"
        else
            echo -e "${YELLOW}⚠${NC} $service_name process not found (PID: $pid)"
        fi
        rm -f "$pidfile"
    else
        echo -e "${YELLOW}⚠${NC} No PID file found for $service_name"
    fi
}

# Stop all services
echo -e "\n${BLUE}Stopping services...${NC}"

# Stop Java processes
kill_by_pid_file ".pids/quarkus.pid" "Quarkus API + Frontend"
kill_by_pid_file ".pids/inventory.pid" "Flink Inventory Job"

# Cleanup PID directory
rm -rf .pids

# Stop Docker containers
echo -e "\n${BLUE}Stopping Docker containers...${NC}"
if docker compose down; then
    echo -e "${GREEN}✓${NC} Docker containers stopped"
else
    echo -e "${YELLOW}⚠${NC} No Docker containers were running"
fi

# Kill any remaining processes by name (fallback)
echo -e "\n${BLUE}Cleaning up any remaining processes...${NC}"

# Kill any remaining Java processes related to our services
pkill -f "InventoryManagementJob" 2>/dev/null && echo -e "${GREEN}✓${NC} Killed remaining inventory processes"
pkill -f "quarkus" 2>/dev/null && echo -e "${GREEN}✓${NC} Killed remaining Quarkus processes"

# Kill any remaining Node processes on port 3000
lsof -ti:3000 | xargs kill 2>/dev/null && echo -e "${GREEN}✓${NC} Killed processes on port 3000"

echo -e "\n${GREEN}========================================"
echo -e "✅ All services have been stopped!"
echo -e "========================================${NC}"
echo
echo -e "${BLUE}To restart the system: ${GREEN}./start-all.sh${NC}"