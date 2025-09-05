#!/bin/bash

# ============================================
# SDKman Installation Script for KartShoppe
# ============================================

set -e

echo "🚀 KartShoppe SDKman Installation Script"
echo "========================================"
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

# Check if SDKman is already installed
if [ -d "$HOME/.sdkman" ]; then
    print_warning "SDKman is already installed at ~/.sdkman"
    echo -n "Do you want to reinstall? (y/N): "
    read -r response
    if [[ ! "$response" =~ ^[Yy]$ ]]; then
        print_info "Skipping SDKman installation"
    else
        print_info "Removing existing SDKman installation..."
        rm -rf "$HOME/.sdkman"
    fi
fi

# Install SDKman if not present
if [ ! -d "$HOME/.sdkman" ]; then
    print_info "Installing SDKman..."
    
    # Download and install SDKman
    curl -s "https://get.sdkman.io" | bash
    
    if [ $? -eq 0 ]; then
        print_success "SDKman installed successfully!"
    else
        print_error "Failed to install SDKman"
        exit 1
    fi
else
    print_info "SDKman is already installed"
fi

# Source SDKman
print_info "Initializing SDKman..."
export SDKMAN_DIR="$HOME/.sdkman"
[[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]] && source "$HOME/.sdkman/bin/sdkman-init.sh"

# Verify SDKman installation
if command -v sdk &> /dev/null; then
    print_success "SDKman is ready!"
    sdk version
else
    print_error "SDKman command not found. Please restart your terminal and run ./setup-java.sh"
    echo
    print_info "Add this to your shell profile (.bashrc, .zshrc, etc.):"
    echo 'export SDKMAN_DIR="$HOME/.sdkman"'
    echo '[[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]] && source "$HOME/.sdkman/bin/sdkman-init.sh"'
    exit 1
fi

echo
print_success "SDKman installation complete!"
echo
print_info "Next steps:"
echo "  1. Restart your terminal or run: source ~/.sdkman/bin/sdkman-init.sh"
echo "  2. Run ./setup-java.sh to install Java 17"
echo "  3. Run ./gradlew :quarkus-api:quarkusDev to start KartShoppe"
echo

# Ask if user wants to install Java now
echo -n "Would you like to install Java 17 now? (Y/n): "
read -r response
if [[ ! "$response" =~ ^[Nn]$ ]]; then
    print_info "Installing Java 17..."
    ./setup-java.sh
fi