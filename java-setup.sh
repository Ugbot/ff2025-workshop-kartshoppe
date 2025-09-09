#!/bin/bash

# Java Version Configuration Script
# This script helps manage different Java versions for different components

echo "🔧 Java Version Configuration"
echo "============================="

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Check current Java version
CURRENT_JAVA=$(java -version 2>&1 | head -n 1)
echo -e "\nCurrent Java version: ${BLUE}$CURRENT_JAVA${NC}"

# Check if SDKMAN is installed
if [ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
    source "$HOME/.sdkman/bin/sdkman-init.sh"
    echo -e "${GREEN}✓ SDKMAN detected${NC}"
    
    echo -e "\n${YELLOW}Recommended Setup:${NC}"
    echo "1. Install Java 11 for Flink: sdk install java 11.0.25-tem"
    echo "2. Install Java 17 for Quarkus: sdk install java 17.0.9-tem"
    echo ""
    echo "To switch between versions:"
    echo "  For Flink jobs: sdk use java 11.0.25-tem"
    echo "  For Quarkus: sdk use java 17.0.9-tem"
else
    echo -e "${YELLOW}⚠ SDKMAN not detected${NC}"
    echo ""
    echo "Install SDKMAN for easy Java version management:"
    echo "  curl -s \"https://get.sdkman.io\" | bash"
fi

echo -e "\n${BLUE}Java Requirements:${NC}"
echo "• Flink modules: Java 11"
echo "• Quarkus API: Java 17+"
echo "• Models/Common: Java 11+"

echo -e "\n${BLUE}Gradle Configuration:${NC}"
echo "The project is configured to use:"
echo "• Java 11 for all flink-* modules"
echo "• Java 17 for quarkus-api module"
echo "• Java 11 as default for other modules"

echo -e "\n${YELLOW}Note:${NC} Gradle toolchains will automatically use the correct"
echo "Java version if both are installed on your system."