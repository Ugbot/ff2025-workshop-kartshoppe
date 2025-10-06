#!/bin/bash

echo "🔨 Building Flink Jobs with Java 11"
echo "===================================="

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

# Function to check Java version
check_java_version() {
    local version=$(java -version 2>&1 | head -n 1 | sed 's/.*version "\([0-9]*\).*/\1/')
    echo "$version"
}

# Save current Java settings
ORIGINAL_JAVA_HOME=$JAVA_HOME
ORIGINAL_PATH=$PATH

echo -e "\n${BLUE}Current Java version:${NC}"
java -version 2>&1 | head -n 1

# Check if Java 11 is available
JAVA_VERSION=$(check_java_version)

if [ "$JAVA_VERSION" != "11" ]; then
    echo -e "\n${YELLOW}⚠ Java 11 is required for building Flink jobs${NC}"
    
    # Try to use SDKMAN if available
    if [ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
        echo "Attempting to switch to Java 11 using SDKMAN..."
        source "$HOME/.sdkman/bin/sdkman-init.sh"
        
        # Check if Java 11 is installed
        if sdk list java installed | grep -q "11\."; then
            # Find the installed Java 11 version
            JAVA11_VERSION=$(sdk list java installed | grep "11\." | head -1 | awk '{print $NF}')
            echo "Found Java 11: $JAVA11_VERSION"
            sdk use java "$JAVA11_VERSION"
        else
            echo -e "${RED}✗ Java 11 not found in SDKMAN${NC}"
            echo "Install it with: sdk install java 11.0.25-tem"
            exit 1
        fi
    else
        # Try to find Java 11 manually
        echo "Looking for Java 11 installation..."
        
        # Common Java 11 locations
        JAVA11_LOCATIONS=(
            "/usr/lib/jvm/java-11-openjdk"
            "/usr/lib/jvm/java-11-openjdk-amd64"
            "/Library/Java/JavaVirtualMachines/temurin-11.jdk/Contents/Home"
            "/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home"
            "$HOME/.sdkman/candidates/java/11"*
        )
        
        JAVA11_HOME=""
        for loc in "${JAVA11_LOCATIONS[@]}"; do
            if [ -d "$loc" ] && [ -f "$loc/bin/java" ]; then
                JAVA11_HOME="$loc"
                break
            fi
        done
        
        if [ -n "$JAVA11_HOME" ]; then
            echo -e "${GREEN}✓ Found Java 11 at: $JAVA11_HOME${NC}"
            export JAVA_HOME="$JAVA11_HOME"
            export PATH="$JAVA_HOME/bin:$PATH"
        else
            echo -e "${RED}✗ Java 11 not found${NC}"
            echo "Please install Java 11 or use SDKMAN"
            exit 1
        fi
    fi
    
    echo -e "\n${BLUE}Switched to Java version:${NC}"
    java -version 2>&1 | head -n 1
fi

# Build Flink modules
echo -e "\n${BLUE}Building Flink modules...${NC}"

FLINK_MODULES=(
    "flink-common"
    "flink-inventory"
    "flink-chat-pipeline"
    "flink-datagen"
    "flink-ecommerce-processor"
    "flink-recommendations"
    "flink-shopping-assistant"
)

# First build common dependencies
echo -e "\n${BLUE}Building common dependencies...${NC}"
./gradlew :models:build :flink-common:build -x test

# Build each Flink module
for module in "${FLINK_MODULES[@]}"; do
    if [ -d "$module" ]; then
        echo -e "\n${BLUE}Building $module...${NC}"
        
        # Check if it has shadowJar task
        if grep -q "shadowJar" "$module/build.gradle" 2>/dev/null; then
            ./gradlew ":$module:shadowJar" -x test
            
            # Check if JAR was created
            JAR_FILE="$module/build/libs/$module.jar"
            if [ -f "$JAR_FILE" ]; then
                echo -e "${GREEN}✓ Built: $JAR_FILE${NC}"
            else
                # Try alternative naming
                JAR_FILE="$module/build/libs/$module-all.jar"
                if [ -f "$JAR_FILE" ]; then
                    echo -e "${GREEN}✓ Built: $JAR_FILE${NC}"
                else
                    echo -e "${YELLOW}⚠ JAR not found for $module${NC}"
                fi
            fi
        else
            ./gradlew ":$module:build" -x test
            echo -e "${GREEN}✓ Built: $module${NC}"
        fi
    else
        echo -e "${YELLOW}⚠ Module not found: $module${NC}"
    fi
done

# Restore original Java settings if changed
if [ "$ORIGINAL_JAVA_HOME" != "$JAVA_HOME" ]; then
    echo -e "\n${BLUE}Restoring original Java settings...${NC}"
    export JAVA_HOME="$ORIGINAL_JAVA_HOME"
    export PATH="$ORIGINAL_PATH"
    
    # If using SDKMAN, switch back
    if [ -s "$HOME/.sdkman/bin/sdkman-init.sh" ] && [ "$JAVA_VERSION" != "11" ]; then
        sdk use java system 2>/dev/null || true
    fi
fi

echo -e "\n${GREEN}========================================"
echo -e "✅ Flink jobs built successfully!"
echo -e "========================================${NC}"
echo
echo -e "${BLUE}Built JARs:${NC}"
for module in "${FLINK_MODULES[@]}"; do
    if [ -d "$module/build/libs" ]; then
        ls -lh "$module/build/libs/"*.jar 2>/dev/null | awk '{print "  " $NF}'
    fi
done
echo
echo -e "${YELLOW}Note:${NC} These JARs were built with Java 11 but can run on Java 17"
echo "You can now run ./start-all.sh with Java 17"