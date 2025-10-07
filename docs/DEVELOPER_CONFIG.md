# 🔧 Developer Configuration Guide

## Quick Command-Line Options

### Disable Inventory Job
```bash
# Run without the Flink inventory job
./gradlew :quarkus-api:quarkusDev -Dflink.jobs.inventory.enabled=false
```

### Disable Kafka Dev Services (Use External)
```bash
# Use external Redpanda/Kafka instead of Testcontainers
./gradlew :quarkus-api:quarkusDev -Dquarkus.kafka.devservices.enabled=false
```

### Change Kafka Port
```bash
# Use a different port for Kafka
./gradlew :quarkus-api:quarkusDev -Dquarkus.kafka.devservices.port=29092
```

### Combine Multiple Options
```bash
# No Flink job + External Kafka
./gradlew :quarkus-api:quarkusDev \
  -Dflink.jobs.inventory.enabled=false \
  -Dquarkus.kafka.devservices.enabled=false
```

## Configuration in application.properties

### Current Settings
```properties
# Flink Jobs Configuration
%dev.flink.jobs.inventory.enabled=true    # Set to false to disable
%test.flink.jobs.inventory.enabled=false  # Disabled in tests
%prod.flink.jobs.inventory.enabled=false  # Disabled in production

# Kafka Dev Services
%dev.quarkus.kafka.devservices.enabled=true
%dev.quarkus.kafka.devservices.port=19092
```

### Create a Custom Profile

Create `application-noflinkjobs.properties`:
```properties
# Profile without Flink jobs
quarkus.profile.parent=dev
flink.jobs.inventory.enabled=false
```

Then run:
```bash
./gradlew :quarkus-api:quarkusDev -Dquarkus.profile=noflinkjobs
```

## Environment Variables

You can also use environment variables:

```bash
# Disable inventory job via env var
export FLINK_JOBS_INVENTORY_ENABLED=false
./gradlew :quarkus-api:quarkusDev

# Or inline
FLINK_JOBS_INVENTORY_ENABLED=false ./gradlew :quarkus-api:quarkusDev
```

## Common Developer Scenarios

### 1. Frontend Development Only
```bash
# No backend services needed
./gradlew :quarkus-api:quarkusDev \
  -Dflink.jobs.inventory.enabled=false \
  -Dquarkus.kafka.devservices.enabled=false
```

### 2. API Development (No Flink)
```bash
# Kafka but no Flink jobs
./gradlew :quarkus-api:quarkusDev \
  -Dflink.jobs.inventory.enabled=false
```

### 3. Full Stack Development
```bash
# Everything enabled (default)
./gradlew :quarkus-api:quarkusDev
```

### 4. Using External Services
```bash
# External Kafka + External Flink
docker compose up -d redpanda
./gradlew :quarkus-api:quarkusDev \
  -Dquarkus.kafka.devservices.enabled=false \
  -Dflink.jobs.inventory.enabled=false

# Then manually start Flink job if needed
java -cp flink-inventory/build/libs/flink-inventory.jar \
  com.ververica.composable_job.flink.inventory.InventoryManagementJob
```

## Debug Configurations

### Enable Debug Logging
```properties
# In application.properties
%dev.quarkus.log.category."com.ververica".level=DEBUG
%dev.quarkus.log.category."io.quarkus.kafka".level=DEBUG
```

### Remote Debugging
```bash
# Enable debug on port 5005
./gradlew :quarkus-api:quarkusDev -Ddebug=5005
```

### Verbose Output
```bash
# See what's happening
./gradlew :quarkus-api:quarkusDev --info
```

## Performance Tuning

### Faster Startup (Skip Unused Services)
```bash
# Minimal startup for UI work
./gradlew :quarkus-api:quarkusDev \
  -Dflink.jobs.inventory.enabled=false \
  -Dquarkus.kafka.devservices.enabled=false \
  -Dquarkus.live-reload.instrumentation=false
```

### Memory Settings
```bash
# Increase memory if needed
export JAVA_OPTS="-Xmx2g -Xms1g"
./gradlew :quarkus-api:quarkusDev
```

## Troubleshooting Flags

### Reset Everything
```bash
# Clean build
./gradlew clean
rm -rf quarkus-api/.quinoa
docker system prune -a

# Fresh start
./gradlew :quarkus-api:quarkusDev
```

### Skip Tests
```bash
# Build without tests
./gradlew build -x test
```

### Force Kafka Recreation
```bash
# New container each time
./gradlew :quarkus-api:quarkusDev \
  -Dquarkus.kafka.devservices.reuse=false
```

## Monitoring What's Running

### Check Active Services
```bash
# See what Quarkus started
curl http://localhost:8080/q/health/ready | jq

# Check if inventory job is running
curl http://localhost:8080/api/ecommerce/inventory/state | jq

# List Docker containers
docker ps | grep redpanda
```

### View Configuration
```bash
# See active configuration
curl http://localhost:8080/q/dev/io.quarkus.quarkus-vertx-http/config
```

## Tips & Tricks

1. **Profile Switching**: Use `-Dquarkus.profile=` to switch between configurations
2. **Hot Reload**: Changes to properties are picked up without restart
3. **Dev UI**: Access http://localhost:8080/q/dev for developer tools
4. **Config Editor**: Use the Dev UI to edit configuration live

## Summary of All Flags

| Flag | Purpose | Example |
|------|---------|---------|
| `flink.jobs.inventory.enabled` | Enable/disable inventory Flink job | `=false` |
| `quarkus.kafka.devservices.enabled` | Use Testcontainers for Kafka | `=false` |
| `quarkus.kafka.devservices.port` | Kafka port | `=29092` |
| `quarkus.profile` | Configuration profile | `=dev` |
| `debug` | Remote debugging port | `=5005` |
| `quarkus.live-reload.instrumentation` | Hot reload instrumentation | `=false` |

---

Remember: All these options can be combined as needed for your specific development scenario!