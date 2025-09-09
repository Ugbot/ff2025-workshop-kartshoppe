# Available Scripts

## Main Scripts

### `./start-all.sh`
Starts the complete KartShoppe platform including:
- Redpanda (Kafka-compatible message broker)
- Quarkus API with Quinoa frontend integration
- Flink Inventory Management Job
- KartShoppe React frontend

### `./stop-all.sh`
Gracefully stops all running services and Docker containers.

### `./clean.sh`
Cleans the entire environment:
- Stops all services
- Removes Docker containers
- Cleans build artifacts
- Removes logs and temporary files

## Component-Specific Scripts

### `./start-kartshoppe.sh`
Starts only the KartShoppe e-commerce components:
- Redpanda
- Quarkus API
- React frontend

### `./start-pipeline.sh`
Starts the data pipeline components:
- Redpanda
- Quarkus API
- Flink data generation jobs

### `./start-inventory.sh`
Starts only the Flink Inventory Management job.
Requires Redpanda to be running.

## Utility Scripts

### `./test-setup.sh`
Verifies your environment is properly configured:
- Checks Java versions (11 for Flink, 17 for Quarkus)
- Verifies Docker installation
- Tests Redpanda connectivity
- Validates Node.js for frontend
- Confirms Gradle wrapper functionality

### `./java-setup.sh`
Provides guidance for managing multiple Java versions:
- Instructions for SDKMAN installation
- Commands for installing Java 11 and 17
- Tips for switching between versions

### `./fix-gradle-wrapper.sh`
Fixes missing gradle-wrapper.jar issue:
- Downloads the correct gradle-wrapper.jar
- Required if you get "Could not find or load main class org.gradle.wrapper.GradleWrapperMain"
- Automatically detects the correct Gradle version

## Removed Legacy Scripts

The following scripts have been removed as they're no longer needed:
- `install-sdkman.sh` - Replaced by java-setup.sh
- `setup-java.sh` - Replaced by java-setup.sh
- `quick-start.sh` - Functionality merged into start-all.sh
- `setup.sh` - Replaced by simpler start scripts
- `refactor-packages.sh` - One-time migration script

## Docker Management

All Docker services are defined in `docker-compose.yml`:
- **redpanda**: Kafka-compatible streaming platform (port 19092)
- **redpanda-console**: Web UI for Redpanda (port 8085)
- **redpanda-init-topics**: One-time topic creation

## Java Version Requirements

The project uses different Java versions for different components:
- **Flink modules**: Java 11
- **Quarkus API**: Java 17
- **Models/Common**: Java 11+

Gradle toolchains automatically handle version selection if both are installed.