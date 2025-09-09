#!/bin/bash

echo "🔍 Testing Unified Kafka/Redpanda Setup"
echo "========================================"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Test 1: Check Gradle wrapper
echo -e "\n1. Checking Gradle wrapper..."
if [ -f "./gradlew" ] && [ -x "./gradlew" ]; then
    echo -e "${GREEN}✓ Gradle wrapper found and executable${NC}"
else
    echo -e "${RED}✗ Gradle wrapper missing or not executable${NC}"
    exit 1
fi

# Test 2: Check Docker
echo -e "\n2. Checking Docker..."
if command -v docker &> /dev/null; then
    echo -e "${GREEN}✓ Docker is installed${NC}"
else
    echo -e "${RED}✗ Docker is not installed${NC}"
    exit 1
fi

# Test 3: Check Redpanda status
echo -e "\n3. Checking Redpanda containers..."
if docker ps | grep -q redpanda; then
    echo -e "${GREEN}✓ Redpanda is running${NC}"
    
    # Check port 19092
    if nc -z localhost 19092 2>/dev/null; then
        echo -e "${GREEN}✓ Redpanda port 19092 is accessible${NC}"
    else
        echo -e "${YELLOW}⚠ Redpanda port 19092 is not accessible${NC}"
    fi
else
    echo -e "${YELLOW}⚠ Redpanda is not running${NC}"
    echo "  Run: docker compose up -d redpanda redpanda-console"
fi

# Test 4: Check Node.js for frontend
echo -e "\n4. Checking Node.js..."
if command -v node &> /dev/null; then
    NODE_VERSION=$(node --version)
    echo -e "${GREEN}✓ Node.js is installed: $NODE_VERSION${NC}"
else
    echo -e "${RED}✗ Node.js is not installed${NC}"
    echo "  Frontend build will fail without Node.js"
fi

# Test 5: Check frontend dependencies
echo -e "\n5. Checking frontend dependencies..."
if [ -d "kartshoppe-frontend/node_modules" ]; then
    echo -e "${GREEN}✓ Frontend dependencies are installed${NC}"
else
    echo -e "${YELLOW}⚠ Frontend dependencies not installed${NC}"
    echo "  Run: cd kartshoppe-frontend && npm install"
fi

# Test 6: Test Gradle build
echo -e "\n6. Testing Gradle build..."
if ./gradlew :models:build > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Gradle build works${NC}"
else
    echo -e "${YELLOW}⚠ Gradle build needs configuration${NC}"
    echo "  The project requires both Java 11 (for Flink) and Java 17 (for Quarkus)"
    echo "  Run ./java-setup.sh for detailed instructions"
fi

# Test 7: Check Java versions
echo -e "\n7. Checking Java versions..."
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1)
    JAVA_MAJOR=$(java -version 2>&1 | head -n 1 | sed 's/.*version "\([0-9]*\).*/\1/')
    
    echo -e "  Current Java: ${BLUE}$JAVA_VERSION${NC}"
    
    if [ "$JAVA_MAJOR" -eq 11 ]; then
        echo -e "${GREEN}✓ Java 11 detected (good for Flink)${NC}"
        echo -e "${YELLOW}  Note: Quarkus requires Java 17 (handled by Gradle toolchain)${NC}"
    elif [ "$JAVA_MAJOR" -ge 17 ]; then
        echo -e "${GREEN}✓ Java 17+ detected (good for Quarkus)${NC}"
        echo -e "${YELLOW}  Note: Flink modules prefer Java 11 (handled by Gradle toolchain)${NC}"
    else
        echo -e "${RED}✗ Java version is too old: $JAVA_VERSION${NC}"
        echo -e "${YELLOW}  Minimum: Java 11 for Flink, Java 17 for Quarkus${NC}"
    fi
    
    echo -e "\n  ${BLUE}Recommended: Install both Java 11 and 17 using SDKMAN${NC}"
    echo "  Run ./java-setup.sh for detailed instructions"
else
    echo -e "${RED}✗ Java is not installed${NC}"
    exit 1
fi

echo -e "\n========================================"
echo -e "${GREEN}Setup verification complete!${NC}"
echo -e "\nAvailable commands:"
echo -e "  ${YELLOW}./start-all.sh${NC}         - Start full platform"
echo -e "  ${YELLOW}./start-kartshoppe.sh${NC}  - KartShoppe e-commerce only"
echo -e "  ${YELLOW}./start-pipeline.sh${NC}    - Data pipeline only"
echo -e "  ${YELLOW}./start-inventory.sh${NC}   - Inventory management only"
echo -e "  ${YELLOW}./stop-all.sh${NC}          - Stop all services"
echo -e "  ${YELLOW}./clean.sh${NC}             - Clean environment"
echo -e "  ${YELLOW}./java-setup.sh${NC}        - Java version help"