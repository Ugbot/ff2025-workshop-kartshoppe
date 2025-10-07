# Available Scripts

## 🎓 Pattern Learning Modules

### Flink Pattern Examples
The project includes standalone pattern learning modules for workshop training:

**Inventory Management Patterns** (`flink-inventory/patterns/`):
- **Pattern 01: Hybrid Source** - Bootstrap from files → stream from Kafka
- **Pattern 02: Keyed State** - Per-key state management with fault tolerance
- **Pattern 03: Timers** - Timeout detection and scheduled tasks
- **Pattern 04: Side Outputs** - Multi-way event routing

Each pattern includes:
- Standalone runnable example
- Comprehensive README with exercises
- Production tips and common pitfalls

**Run pattern examples:**
```bash
# Pattern 01: Hybrid Source
./gradlew :flink-inventory:run -PmainClass=com.ververica.composable_job.flink.inventory.patterns.hybrid_source.HybridSourceExample

# Pattern 02: Keyed State
./gradlew :flink-inventory:run -PmainClass=com.ververica.composable_job.flink.inventory.patterns.keyed_state.KeyedStateExample

# Pattern 03: Timers
./gradlew :flink-inventory:run -PmainClass=com.ververica.composable_job.flink.inventory.patterns.timers.TimerExample

# Pattern 04: Side Outputs
./gradlew :flink-inventory:run -PmainClass=com.ververica.composable_job.flink.inventory.patterns.side_outputs.SideOutputExample
```

See `flink-inventory/src/main/java/com/ververica/composable_job/flink/inventory/patterns/README.md` for the complete learning guide.

## Build Scripts

### `./build-all.sh`
Builds the entire platform:
- Builds Flink jobs with Java 11 (if available)
- Builds Quarkus API with current Java version
- Prepares frontend dependencies

### `./build-flink.sh`
Specifically builds Flink jobs with Java 11:
- Automatically switches to Java 11 if available
- Builds all Flink modules with shadowJar
- Reverts to original Java version after building

## Main Scripts

### `./start-all.sh`
Starts the complete KartShoppe platform including:
- Redpanda (Kafka-compatible message broker)
- Quarkus API with Quinoa frontend integration (single process)
- Flink Inventory Management Job
- Note: Frontend is served at http://localhost:8080 (not /kartshoppe)

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