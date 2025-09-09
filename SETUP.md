# Setup Instructions

## Prerequisites

1. **Java Versions** (Mixed requirements)
   - **Java 11**: Required for Flink modules
   - **Java 17**: Required for Quarkus API
   - Check version: `java -version`
   - Recommended: Use SDKMAN for managing multiple versions
   
   ```bash
   # Install SDKMAN
   curl -s "https://get.sdkman.io" | bash
   
   # Install both Java versions
   sdk install java 11.0.25-tem
   sdk install java 17.0.9-tem
   
   # Set Java 11 as default (for Flink)
   sdk default java 11.0.25-tem
   ```
   
2. **Docker** 
   - Required for running Redpanda (Kafka)
   
3. **Node.js** (v18+ recommended)
   - Required for frontend development
   
4. **Git**
   - For version control

## Quick Start

1. **Verify Setup**
   ```bash
   ./test-setup.sh
   ```

2. **Start Everything**
   ```bash
   ./start-all.sh
   ```

## Individual Components

- **KartShoppe E-commerce**: `./start-kartshoppe.sh`
- **Data Pipeline**: `./start-pipeline.sh`  
- **Inventory Management**: `./start-inventory.sh`

## Architecture

- **Message Broker**: Redpanda (Kafka-compatible) on port 19092
- **Backend**: Quarkus with Quinoa for frontend integration
- **Frontend**: React + Vite (integrated via Quinoa)
- **Stream Processing**: Apache Flink jobs

## Troubleshooting

### Gradle Wrapper Error
If you see "Could not find or load main class org.gradle.wrapper.GradleWrapperMain":
```bash
# The gradle-wrapper.jar file is missing. Run:
./fix-gradle-wrapper.sh

# Or manually download it:
curl -L -o gradle/wrapper/gradle-wrapper.jar \
  https://raw.githubusercontent.com/gradle/gradle/v8.14.1/gradle/wrapper/gradle-wrapper.jar
```

### Java Version Issues

The project uses different Java versions for different components:
- **Flink modules**: Java 11
- **Quarkus API**: Java 17

Gradle toolchains will automatically use the correct version if both are installed.

To manually switch between versions:
```bash
# For building/running Flink jobs
sdk use java 11.0.25-tem

# For building/running Quarkus
sdk use java 17.0.9-tem
```

If you see version errors, ensure both Java 11 and 17 are installed:
```bash
sdk list java --installed
```

### Port Conflicts
- Redpanda: 19092 (Kafka), 18081 (Schema Registry), 8085 (Console)
- Quarkus: 8080
- Frontend Dev: 3000 (standalone), 5173 (via Quinoa)

### Clean Restart
```bash
# Stop all containers
docker compose down

# Clean build artifacts
./gradlew clean

# Remove old containers
docker ps -a | grep -E "kafka|redpanda" | awk '{print $1}' | xargs docker rm -f

# Start fresh
./start-all.sh
```

## Gradle Wrapper

This project uses the Gradle wrapper for consistency:
- Use `./gradlew` instead of `gradle`
- No need to install Gradle separately
- Wrapper files are included in the repository