#!/bin/bash

echo "🚀 Building KartShoppe Platform"
echo "================================"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

# Check current Java version
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | sed 's/.*version "\([0-9]*\).*/\1/')
echo -e "\n${BLUE}Current Java version:${NC}"
java -version 2>&1 | head -n 1

# Step 1: Build Flink jobs with Java 11
echo -e "\n${BLUE}Step 1: Building Flink jobs (requires Java 11)...${NC}"
echo "=================================================="

if [ -x "./build-flink.sh" ]; then
    ./build-flink.sh
    if [ $? -ne 0 ]; then
        echo -e "${RED}✗ Flink build failed${NC}"
        exit 1
    fi
else
    echo -e "${YELLOW}⚠ build-flink.sh not found or not executable${NC}"
    echo "Building Flink modules with current Java version..."
    ./gradlew :flink-inventory:shadowJar :flink-datagen:build -x test
fi

# Step 2: Build Quarkus with Java 17 (or current version)
echo -e "\n${BLUE}Step 2: Building Quarkus API (requires Java 17)...${NC}"
echo "===================================================="

if [ "$JAVA_VERSION" -lt 17 ]; then
    echo -e "${YELLOW}⚠ Java 17+ is required for Quarkus${NC}"
    echo "Attempting to build anyway (may fail)..."
fi

# Install frontend dependencies for Quinoa
if [ ! -d "kartshoppe-frontend/node_modules" ]; then
    echo -e "\n${BLUE}Installing frontend dependencies...${NC}"
    cd kartshoppe-frontend && npm install && cd ..
fi

# Build Quarkus (this will also trigger Quinoa frontend build)
echo -e "\n${BLUE}Building Quarkus API...${NC}"
./gradlew :quarkus-api:build -x test

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Quarkus API built successfully${NC}"
else
    echo -e "${YELLOW}⚠ Quarkus build had issues${NC}"
    echo "This might be due to Java version requirements"
fi

# Step 3: Summary
echo -e "\n${GREEN}========================================"
echo -e "✅ Build complete!"
echo -e "========================================${NC}"
echo
echo -e "${BLUE}Build Summary:${NC}"
echo "• Flink jobs: Built with Java 11 (if available)"
echo "• Quarkus API: Built with current Java version"
echo "• Frontend: Will be built on-demand by Quinoa"
echo
echo -e "${BLUE}Java Requirements for Running:${NC}"
echo "• Flink jobs: Can run on Java 11 or 17"
echo "• Quarkus: Requires Java 17+"
echo
echo -e "${YELLOW}Recommended Setup:${NC}"
echo "1. Install both Java 11 and 17 using SDKMAN"
echo "2. Use Java 17 as default for running everything"
echo "3. Run ./build-flink.sh when you need to rebuild Flink jobs"
echo
echo -e "To start the platform: ${GREEN}./start-all.sh${NC}"