#!/bin/bash

# ================================================
# KartShoppe Universal Setup Script
# Works on macOS, Linux, and WSL
# ================================================

set -e

echo "======================================================"
echo "   🚀 KartShoppe Universal Setup & Launch Script"
echo "======================================================"
echo

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# Detect OS
OS="Unknown"
if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    OS="Linux"
elif [[ "$OSTYPE" == "darwin"* ]]; then
    OS="macOS"
elif [[ "$OSTYPE" == "cygwin" ]] || [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "win32" ]]; then
    OS="Windows"
fi

echo -e "${CYAN}Detected OS: $OS${NC}"
echo

# Functions
print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_step() {
    echo
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${CYAN}$1${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo
}

# Check if running in WSL
is_wsl() {
    if grep -qi microsoft /proc/version 2>/dev/null; then
        return 0
    fi
    return 1
}

# ================================================
# STEP 1: Check Docker
# ================================================
print_step "Step 1: Checking Docker"

check_docker() {
    if command -v docker &> /dev/null; then
        if docker ps &> /dev/null; then
            print_success "Docker is installed and running"
            return 0
        else
            print_warning "Docker is installed but not running"
            
            if [[ "$OS" == "macOS" ]]; then
                print_info "Starting Docker Desktop..."
                open -a Docker
                print_info "Waiting for Docker to start (30 seconds)..."
                sleep 30
            elif is_wsl; then
                print_error "Please start Docker Desktop on Windows"
                print_info "Make sure WSL integration is enabled in Docker Desktop settings"
                exit 1
            else
                print_error "Please start Docker and run this script again"
                exit 1
            fi
        fi
    else
        print_error "Docker is not installed!"
        print_info "Please install Docker Desktop from:"
        echo "  https://www.docker.com/products/docker-desktop"
        exit 1
    fi
}

check_docker

# ================================================
# STEP 2: Install/Check Java
# ================================================
print_step "Step 2: Setting up Java 17"

# Check if Java 17+ is already installed
check_java() {
    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f 2 | cut -d'.' -f 1)
        if [[ "$JAVA_VERSION" -ge 17 ]]; then
            print_success "Java $JAVA_VERSION is already installed"
            return 0
        else
            print_warning "Java $JAVA_VERSION found, but Java 17+ is required"
            return 1
        fi
    else
        print_warning "Java is not installed"
        return 1
    fi
}

install_sdkman() {
    if [ ! -d "$HOME/.sdkman" ]; then
        print_info "Installing SDKman..."
        curl -s "https://get.sdkman.io" | bash
        
        # Source SDKman
        export SDKMAN_DIR="$HOME/.sdkman"
        [[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]] && source "$HOME/.sdkman/bin/sdkman-init.sh"
        
        print_success "SDKman installed"
    else
        # Source SDKman
        export SDKMAN_DIR="$HOME/.sdkman"
        [[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]] && source "$HOME/.sdkman/bin/sdkman-init.sh"
        print_success "SDKman is already installed"
    fi
}

install_java_with_sdkman() {
    print_info "Installing Java 17 with SDKman..."
    
    # Try to install Java 17
    if sdk install java 17.0.13-tem < /dev/null 2>/dev/null; then
        sdk use java 17.0.13-tem
        print_success "Java 17 installed via SDKman"
    elif sdk install java 17.0.12-tem < /dev/null 2>/dev/null; then
        sdk use java 17.0.12-tem
        print_success "Java 17 installed via SDKman"
    else
        # Fallback to any Java 17
        JAVA_17=$(sdk list java | grep -E "17\." | grep -v installed | head -1 | awk '{print $NF}')
        if [ -n "$JAVA_17" ]; then
            sdk install java "$JAVA_17" < /dev/null
            sdk use java "$JAVA_17"
            print_success "Java 17 installed via SDKman"
        else
            print_error "Could not find Java 17 in SDKman"
            return 1
        fi
    fi
    
    # Create .sdkmanrc
    java -version 2>&1 | grep -oP '17\.\d+\.\d+' | head -1 | xargs -I {} echo "java={}-tem" > .sdkmanrc 2>/dev/null || true
}

# Main Java installation logic
if ! check_java; then
    print_info "Need to install Java 17..."
    
    # Try SDKman first
    install_sdkman
    
    if command -v sdk &> /dev/null; then
        install_java_with_sdkman
    else
        print_error "Could not install SDKman"
        print_info "Please install Java 17 manually and run this script again"
        exit 1
    fi
fi

# Verify Java is working
java -version

# ================================================
# STEP 3: Build Project
# ================================================
print_step "Step 3: Building the Project"

print_info "Running Gradle build (this might take a few minutes on first run)..."

if ./gradlew build -x test --no-daemon 2>/dev/null; then
    print_success "Build successful"
else
    print_warning "Build had some issues, but continuing..."
fi

# ================================================
# STEP 4: Launch Application
# ================================================
print_step "Step 4: Launching KartShoppe"

echo
echo -e "${GREEN}════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  🎉 Setup Complete! Starting KartShoppe...${NC}"
echo -e "${GREEN}════════════════════════════════════════════════════════${NC}"
echo
echo -e "${CYAN}The application will be available at:${NC}"
echo -e "${YELLOW}  http://localhost:8080/kartshoppe/${NC}"
echo
echo -e "${CYAN}What's happening:${NC}"
echo "  • Starting Redpanda (Kafka) in Docker"
echo "  • Creating Kafka topics automatically"
echo "  • Launching Flink inventory job"
echo "  • Starting Quarkus API server"
echo "  • Serving KartShoppe frontend"
echo
echo -e "${YELLOW}Press Ctrl+C to stop all services${NC}"
echo

# Check if user wants to disable Flink job
if [ "$1" == "--no-flink" ]; then
    print_info "Starting without Flink inventory job..."
    ./gradlew :quarkus-api:quarkusDev -Dflink.jobs.inventory.enabled=false
else
    # Start everything
    ./gradlew :quarkus-api:quarkusDev
fi