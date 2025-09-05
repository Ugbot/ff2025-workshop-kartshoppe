# Quarkus Integrated Launch

The KartShoppe demo now supports a fully integrated launch through Quarkus Dev Services!

## Features

When you start Quarkus in dev mode, it automatically:

1. **Starts Redpanda** (Kafka) using Testcontainers
2. **Creates all required topics** automatically
3. **Launches the Flink Inventory Job** in the background
4. **Serves the KartShoppe frontend** through Quinoa
5. **Provides hot-reload** for both backend and frontend

## How to Launch Everything

Simply run:

```bash
# Make sure you have Java 17 set
source ~/.sdkman/bin/sdkman-init.sh && sdk use java 17.0.15-tem

# Start everything with one command
./gradlew :quarkus-api:quarkusDev
```

## What Gets Started

- **Redpanda/Kafka**: Runs in a Docker container on port 19092
- **Quarkus API**: Runs on http://localhost:8080
- **KartShoppe Frontend**: Accessible at http://localhost:8080/kartshoppe/
- **Flink Inventory Job**: Starts automatically after 10 seconds
- **All Kafka Topics**: Created automatically

## Configuration

### Dev Services Settings (application.properties)

```properties
# Enable Kafka dev services
%dev.quarkus.kafka.devservices.enabled=true
%dev.quarkus.kafka.devservices.image-name=docker.redpanda.com/redpandadata/redpanda:v24.2.4

# Enable Flink jobs
%dev.flink.jobs.inventory.enabled=true

# Quinoa serves the frontend
quarkus.quinoa.ui-dir=../kartshoppe-frontend
```

### Disable Components

If you want to disable certain components:

```properties
# Disable Kafka dev services (use external Redpanda)
%dev.quarkus.kafka.devservices.enabled=false

# Disable automatic Flink job startup
%dev.flink.jobs.inventory.enabled=false
```

## Manual Mode (Original Setup)

If you prefer the manual setup with external Docker Compose:

```bash
# Start Redpanda manually
docker compose up -d redpanda redpanda-console

# Then start Quarkus with dev services disabled
./gradlew :quarkus-api:quarkusDev -Dquarkus.kafka.devservices.enabled=false
```

## Benefits

- **Single command** to start everything
- **No manual Docker commands** needed
- **Automatic topic creation**
- **Integrated Flink jobs**
- **Hot reload** for development
- **Clean shutdown** when you stop Quarkus

## Requirements

- Docker Desktop must be running
- Java 17 (managed via SDKman)
- Gradle
- Node.js (automatically installed by Quinoa)

## Troubleshooting

If Redpanda doesn't start:
- Ensure Docker Desktop is running
- Check port 19092 is not in use
- Look at logs in the Quarkus console

If the frontend doesn't load:
- Check http://localhost:8080/kartshoppe/
- Ensure port 5173 is available for dev mode
- Check Quinoa logs in Quarkus console

If Flink job doesn't start:
- Wait 10 seconds after Quarkus starts
- Check logs for "Inventory Management Job started"
- Verify at http://localhost:8080/api/ecommerce/inventory/state