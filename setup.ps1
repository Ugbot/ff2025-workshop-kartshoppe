# ================================================
# KartShoppe Setup Script for Windows PowerShell
# ================================================

Write-Host "======================================================" -ForegroundColor Cyan
Write-Host "   🚀 KartShoppe Setup & Launch Script for Windows" -ForegroundColor Cyan
Write-Host "======================================================" -ForegroundColor Cyan
Write-Host ""

# Check if running as Administrator (recommended but not required)
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole] "Administrator")
if (-not $isAdmin) {
    Write-Host "⚠️  Not running as Administrator. Some features might not work." -ForegroundColor Yellow
    Write-Host "   Consider running PowerShell as Administrator for best results." -ForegroundColor Yellow
    Write-Host ""
}

# Enable script execution if needed
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force

# ================================================
# STEP 1: Check Docker Desktop
# ================================================
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "Step 1: Checking Docker Desktop" -ForegroundColor Cyan
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host ""

$dockerInstalled = $false
$dockerRunning = $false

# Check if Docker is installed
try {
    $dockerVersion = docker --version 2>$null
    if ($dockerVersion) {
        $dockerInstalled = $true
        Write-Host "✅ Docker is installed: $dockerVersion" -ForegroundColor Green
        
        # Check if Docker is running
        docker ps 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) {
            $dockerRunning = $true
            Write-Host "✅ Docker is running" -ForegroundColor Green
        } else {
            Write-Host "⚠️  Docker is installed but not running" -ForegroundColor Yellow
        }
    }
} catch {
    Write-Host "❌ Docker is not installed or not in PATH" -ForegroundColor Red
}

if (-not $dockerInstalled) {
    Write-Host ""
    Write-Host "Docker Desktop is required but not installed!" -ForegroundColor Red
    Write-Host "Please install Docker Desktop from:" -ForegroundColor Yellow
    Write-Host "  https://www.docker.com/products/docker-desktop" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "After installation:" -ForegroundColor Yellow
    Write-Host "  1. Start Docker Desktop" -ForegroundColor White
    Write-Host "  2. Wait for it to fully start" -ForegroundColor White
    Write-Host "  3. Run this script again" -ForegroundColor White
    Write-Host ""
    Read-Host "Press Enter to exit"
    exit 1
}

if (-not $dockerRunning) {
    Write-Host ""
    Write-Host "Starting Docker Desktop..." -ForegroundColor Yellow
    
    # Try to start Docker Desktop
    $dockerDesktopPath = "${env:ProgramFiles}\Docker\Docker\Docker Desktop.exe"
    if (Test-Path $dockerDesktopPath) {
        Start-Process $dockerDesktopPath
        Write-Host "Waiting for Docker to start (30 seconds)..." -ForegroundColor Yellow
        Start-Sleep -Seconds 30
        
        # Check again
        docker ps 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✅ Docker is now running" -ForegroundColor Green
        } else {
            Write-Host "❌ Docker failed to start. Please start it manually and run this script again." -ForegroundColor Red
            Read-Host "Press Enter to exit"
            exit 1
        }
    } else {
        Write-Host "❌ Could not find Docker Desktop. Please start it manually." -ForegroundColor Red
        Read-Host "Press Enter to exit"
        exit 1
    }
}

Write-Host ""

# ================================================
# STEP 2: Check/Install Java
# ================================================
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "Step 2: Setting up Java 17" -ForegroundColor Cyan
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host ""

$javaInstalled = $false
$javaVersion = 0

# Check if Java is installed
try {
    $javaVersionOutput = java -version 2>&1 | Select-String "version"
    if ($javaVersionOutput) {
        $versionString = $javaVersionOutput.ToString()
        if ($versionString -match '\"(\d+)\.') {
            $javaVersion = [int]$matches[1]
        } elseif ($versionString -match '\"(\d+)\"') {
            $javaVersion = [int]$matches[1]
        }
        
        if ($javaVersion -ge 17) {
            $javaInstalled = $true
            Write-Host "✅ Java $javaVersion is installed" -ForegroundColor Green
        } else {
            Write-Host "⚠️  Java $javaVersion found, but Java 17+ is required" -ForegroundColor Yellow
        }
    }
} catch {
    Write-Host "⚠️  Java is not installed or not in PATH" -ForegroundColor Yellow
}

if (-not $javaInstalled) {
    Write-Host ""
    Write-Host "Installing Java 17..." -ForegroundColor Yellow
    
    # Check if Chocolatey is installed
    $chocoInstalled = $false
    try {
        choco --version | Out-Null
        if ($LASTEXITCODE -eq 0) {
            $chocoInstalled = $true
        }
    } catch {}
    
    if ($chocoInstalled) {
        Write-Host "Using Chocolatey to install Java 17..." -ForegroundColor Yellow
        choco install temurin17 -y
        
        # Refresh environment variables
        $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
        
    } else {
        Write-Host "Downloading Java 17 (Temurin)..." -ForegroundColor Yellow
        
        # Download URL for Windows x64
        $javaUrl = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.13%2B11/OpenJDK17U-jdk_x64_windows_hotspot_17.0.13_11.msi"
        $javaInstaller = "$env:TEMP\OpenJDK17.msi"
        
        # Download Java installer
        Write-Host "Downloading from: $javaUrl" -ForegroundColor Gray
        try {
            Invoke-WebRequest -Uri $javaUrl -OutFile $javaInstaller -UseBasicParsing
            Write-Host "✅ Download complete" -ForegroundColor Green
            
            # Install Java
            Write-Host "Installing Java 17..." -ForegroundColor Yellow
            Start-Process msiexec.exe -ArgumentList "/i", $javaInstaller, "/quiet", "/qn" -Wait
            
            # Update PATH
            $javaHome = "${env:ProgramFiles}\Eclipse Adoptium\jdk-17.0.13.11-hotspot"
            if (Test-Path $javaHome) {
                $env:JAVA_HOME = $javaHome
                $env:Path = "$javaHome\bin;$env:Path"
                Write-Host "✅ Java 17 installed successfully" -ForegroundColor Green
            } else {
                Write-Host "⚠️  Java installed but could not find installation directory" -ForegroundColor Yellow
            }
            
            # Clean up
            Remove-Item $javaInstaller -Force -ErrorAction SilentlyContinue
            
        } catch {
            Write-Host "❌ Failed to download/install Java" -ForegroundColor Red
            Write-Host "Error: $_" -ForegroundColor Red
            Write-Host ""
            Write-Host "Please install Java 17 manually from:" -ForegroundColor Yellow
            Write-Host "  https://adoptium.net/temurin/releases/?version=17" -ForegroundColor Cyan
            Read-Host "Press Enter to exit"
            exit 1
        }
    }
    
    # Verify Java installation
    try {
        $javaVersionOutput = java -version 2>&1 | Select-String "version"
        Write-Host "Java installed: $javaVersionOutput" -ForegroundColor Green
    } catch {
        Write-Host "⚠️  Java installation may require a restart of this PowerShell session" -ForegroundColor Yellow
    }
}

Write-Host ""

# ================================================
# STEP 3: Build Project
# ================================================
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "Step 3: Building the Project" -ForegroundColor Cyan
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host ""

Write-Host "Running Gradle build (this might take a few minutes on first run)..." -ForegroundColor Yellow

# Use gradlew.bat for Windows
if (Test-Path ".\gradlew.bat") {
    .\gradlew.bat build -x test --no-daemon 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ Build successful" -ForegroundColor Green
    } else {
        Write-Host "⚠️  Build had some issues, but continuing..." -ForegroundColor Yellow
    }
} else {
    Write-Host "❌ gradlew.bat not found!" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host ""

# ================================================
# STEP 4: Launch Application
# ================================================
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "Step 4: Launching KartShoppe" -ForegroundColor Cyan
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host ""

Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Green
Write-Host "  🎉 Setup Complete! Starting KartShoppe..." -ForegroundColor Green
Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Green
Write-Host ""
Write-Host "The application will be available at:" -ForegroundColor Cyan
Write-Host "  http://localhost:8080/kartshoppe/" -ForegroundColor Yellow
Write-Host ""
Write-Host "What's happening:" -ForegroundColor Cyan
Write-Host "  • Starting Redpanda (Kafka) in Docker" -ForegroundColor White
Write-Host "  • Creating Kafka topics automatically" -ForegroundColor White
Write-Host "  • Launching Flink inventory job" -ForegroundColor White
Write-Host "  • Starting Quarkus API server" -ForegroundColor White
Write-Host "  • Serving KartShoppe frontend" -ForegroundColor White
Write-Host ""
Write-Host "Press Ctrl+C to stop all services" -ForegroundColor Yellow
Write-Host ""

# Check for --no-flink parameter
$noFlink = $false
if ($args -contains "--no-flink") {
    $noFlink = $true
    Write-Host "Starting without Flink inventory job..." -ForegroundColor Yellow
}

# Start the application
if ($noFlink) {
    .\gradlew.bat :quarkus-api:quarkusDev "-Dflink.jobs.inventory.enabled=false"
} else {
    .\gradlew.bat :quarkus-api:quarkusDev
}