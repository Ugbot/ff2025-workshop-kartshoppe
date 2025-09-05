#!/bin/bash

# ============================================
# KartShoppe Quick Start - Zero to Running
# ============================================

set -e

echo "🚀 KartShoppe Quick Start Script"
echo "=================================="
echo "This script will set up everything from scratch!"
echo

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Function to print colored output
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

# Check prerequisites
print_step "Step 1: Checking Prerequisites"

# Check for curl
if ! command -v curl &> /dev/null; then
    print_error "curl is not installed. Please install curl first."
    exit 1
fi
print_success "curl is installed"

# Check for Docker
if ! command -v docker &> /dev/null; then
    print_error "Docker is not installed. Please install Docker Desktop first."
    print_info "Visit: https://www.docker.com/products/docker-desktop"
    exit 1
fi
print_success "Docker is installed"

# Check if Docker is running
if ! docker ps &> /dev/null; then
    print_warning "Docker is not running. Starting Docker..."
    if [[ "$OSTYPE" == "darwin"* ]]; then
        open -a Docker
        print_info "Waiting for Docker to start (30 seconds)..."
        sleep 30
    else
        print_error "Please start Docker manually and run this script again."
        exit 1
    fi
fi
print_success "Docker is running"

# Install SDKman
print_step "Step 2: Setting up SDKman"

if [ ! -d "$HOME/.sdkman" ]; then
    print_info "Installing SDKman..."
    curl -s "https://get.sdkman.io" | bash
    print_success "SDKman installed!"
else
    print_success "SDKman is already installed"
fi

# Source SDKman
export SDKMAN_DIR="$HOME/.sdkman"
[[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]] && source "$HOME/.sdkman/bin/sdkman-init.sh"

# Install Java 17
print_step "Step 3: Setting up Java 17"

if ! sdk list java | grep -q "17.*>>>"; then
    print_info "Installing Java 17 (Temurin)..."
    sdk install java 17.0.13-tem < /dev/null
    sdk default java 17.0.13-tem
    print_success "Java 17 installed and set as default"
else
    print_success "Java 17 is already installed"
    sdk use java 17.0.13-tem 2>/dev/null || true
fi

# Verify Java
java -version

# Create .sdkmanrc
echo "java=17.0.13-tem" > .sdkmanrc
print_success "Created .sdkmanrc file"

# Make scripts executable
print_step "Step 4: Setting up Project Scripts"

chmod +x install-sdkman.sh 2>/dev/null || true
chmod +x setup-java.sh 2>/dev/null || true
chmod +x quick-start.sh 2>/dev/null || true
chmod +x start-all.sh 2>/dev/null || true
chmod +x stop-all.sh 2>/dev/null || true
print_success "Scripts are executable"

# Build the project
print_step "Step 5: Building the Project"

print_info "Running Gradle build (this might take a few minutes on first run)..."
./gradlew build -x test --no-daemon || {
    print_warning "Build had some issues, but continuing..."
}

# Start everything with Quarkus
print_step "Step 6: Starting KartShoppe with Quarkus Dev Mode"

print_info "Starting all services with Quarkus..."
print_info "This will:"
echo "  • Start Redpanda (Kafka) in Docker"
echo "  • Create all Kafka topics"
echo "  • Launch the Flink inventory job"
echo "  • Start the Quarkus API"
echo "  • Serve the KartShoppe frontend"
echo

print_success "Setup complete! Starting the application..."
echo
echo -e "${GREEN}════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}    KartShoppe will be available at:${NC}"
echo -e "${GREEN}    http://localhost:8080/kartshoppe/${NC}"
echo -e "${GREEN}════════════════════════════════════════════════════${NC}"
echo
print_info "Starting in 3 seconds..."
sleep 3

# Run Quarkus
./gradlew :quarkus-api:quarkusDev