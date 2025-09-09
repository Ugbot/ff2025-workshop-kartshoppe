#!/bin/bash

echo "🔧 Fixing Gradle Wrapper"
echo "========================"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Check if gradle wrapper jar exists
if [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo -e "${GREEN}✓ Gradle wrapper JAR already exists${NC}"
    exit 0
fi

echo -e "${YELLOW}⚠ Gradle wrapper JAR is missing${NC}"
echo "Downloading gradle wrapper..."

# Create gradle wrapper directory if it doesn't exist
mkdir -p gradle/wrapper

# Download gradle wrapper jar
GRADLE_VERSION=$(grep "distributionUrl" gradle/wrapper/gradle-wrapper.properties | sed 's/.*gradle-\([0-9.]*\)-.*/\1/')
echo "Detected Gradle version: $GRADLE_VERSION"

# Download the wrapper jar from Gradle's GitHub
WRAPPER_URL="https://raw.githubusercontent.com/gradle/gradle/v${GRADLE_VERSION}/gradle/wrapper/gradle-wrapper.jar"
echo "Downloading from: $WRAPPER_URL"

if curl -L -o gradle/wrapper/gradle-wrapper.jar "$WRAPPER_URL"; then
    echo -e "${GREEN}✓ Successfully downloaded gradle-wrapper.jar${NC}"
else
    echo -e "${RED}✗ Failed to download gradle-wrapper.jar${NC}"
    echo ""
    echo "Alternative solutions:"
    echo "1. Ask someone with the project working to share gradle/wrapper/gradle-wrapper.jar"
    echo "2. Install gradle locally and run: gradle wrapper"
    echo "3. Download manually from: https://services.gradle.org/distributions/"
    exit 1
fi

# Make gradlew executable
chmod +x gradlew

echo -e "\n${GREEN}✓ Gradle wrapper fixed!${NC}"
echo "You can now run: ./start-all.sh"