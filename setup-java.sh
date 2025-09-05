#!/bin/bash

# ============================================
# Java 17 Setup Script for KartShoppe
# ============================================

set -e

echo "☕ KartShoppe Java Setup Script"
echo "================================"
echo

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
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

# Source SDKman
if [ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
    print_info "Loading SDKman..."
    source "$HOME/.sdkman/bin/sdkman-init.sh"
else
    print_error "SDKman not found! Please run ./install-sdkman.sh first"
    exit 1
fi

# Verify SDKman is available
if ! command -v sdk &> /dev/null; then
    print_error "SDKman command not available. Please restart your terminal or run:"
    echo "source ~/.sdkman/bin/sdkman-init.sh"
    exit 1
fi

print_success "SDKman loaded successfully"
echo

# Check current Java version
print_info "Checking current Java installation..."
if command -v java &> /dev/null; then
    CURRENT_JAVA=$(java -version 2>&1 | head -n 1)
    print_info "Current Java: $CURRENT_JAVA"
else
    print_warning "No Java installation found"
fi
echo

# List available Java 17 versions
print_info "Available Java 17 versions:"
sdk list java | grep -E "^\s*(17\.)" | head -10 || true
echo

# Recommended Java version
RECOMMENDED_JAVA="17.0.13-tem"
print_info "Recommended version for KartShoppe: Java $RECOMMENDED_JAVA (Temurin)"
echo

# Ask user for Java version
echo -n "Enter Java version to install (press Enter for $RECOMMENDED_JAVA): "
read -r JAVA_VERSION
if [ -z "$JAVA_VERSION" ]; then
    JAVA_VERSION=$RECOMMENDED_JAVA
fi

# Install Java
print_info "Installing Java $JAVA_VERSION..."
sdk install java "$JAVA_VERSION" || {
    print_error "Failed to install Java $JAVA_VERSION"
    print_info "Trying alternative version 17.0.12-tem..."
    JAVA_VERSION="17.0.12-tem"
    sdk install java "$JAVA_VERSION" || {
        print_error "Installation failed. Please check available versions with: sdk list java"
        exit 1
    }
}

print_success "Java $JAVA_VERSION installed successfully!"
echo

# Set as default
echo -n "Set Java $JAVA_VERSION as default? (Y/n): "
read -r response
if [[ ! "$response" =~ ^[Nn]$ ]]; then
    sdk default java "$JAVA_VERSION"
    print_success "Java $JAVA_VERSION set as default"
fi

# Use the Java version
sdk use java "$JAVA_VERSION"
print_success "Now using Java $JAVA_VERSION"
echo

# Verify installation
print_info "Verifying Java installation..."
java -version
javac -version
echo

# Create .sdkmanrc file for the project
print_info "Creating .sdkmanrc file for automatic Java version switching..."
echo "java=$JAVA_VERSION" > .sdkmanrc
print_success ".sdkmanrc created with Java $JAVA_VERSION"
echo

# Test Gradle
print_info "Testing Gradle wrapper..."
if ./gradlew --version &> /dev/null; then
    print_success "Gradle wrapper is working!"
else
    print_warning "Gradle wrapper test failed. This might be normal on first run."
fi
echo

# Final message
print_success "Java setup complete! 🎉"
echo
print_info "You can now run KartShoppe with:"
echo "  ./gradlew :quarkus-api:quarkusDev"
echo
print_info "Or if you need to switch Java versions later:"
echo "  sdk use java $JAVA_VERSION"
echo
print_info "To see all installed Java versions:"
echo "  sdk list java | grep installed"