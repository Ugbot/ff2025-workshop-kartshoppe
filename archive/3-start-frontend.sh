#!/bin/bash

# ==============================================================================
# Script 3: Start Frontend (React/Vite)
# ==============================================================================

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  Step 3: Starting Frontend (React/Vite)                  ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════╝${NC}"
echo ""

# Check if Quarkus is running
if ! lsof -Pi :8080 -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo -e "${RED}✗ Quarkus API is not running on port 8080${NC}"
    echo -e "${YELLOW}  Please run ${GREEN}./2-start-quarkus.sh${YELLOW} first (in another terminal)${NC}"
    exit 1
fi

echo -e "${GREEN}✓${NC} Quarkus API is running"
echo ""

# Check if port 3000 is already in use
if lsof -Pi :3000 -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo -e "${YELLOW}⚠ Port 3000 is already in use${NC}"
    echo -e "${YELLOW}  Killing existing process...${NC}"
    lsof -ti:3000 | xargs kill -9 2>/dev/null || true
    sleep 2
fi

cd kartshoppe-frontend

# Install dependencies if needed
if [ ! -d "node_modules" ]; then
    echo -e "${BLUE}Installing frontend dependencies...${NC}"
    npm install
    echo -e "${GREEN}✓${NC} Dependencies installed"
    echo ""
fi

# Start the frontend
echo -e "${BLUE}Starting Vite development server...${NC}"
echo -e "${YELLOW}This will run in the foreground. Press Ctrl+C to stop.${NC}"
echo ""
echo -e "${GREEN}Frontend will be available at: http://localhost:3000${NC}"
echo ""

# Save PID for cleanup
mkdir -p ../.pids
echo $$ > ../.pids/frontend.pid

npm run dev
