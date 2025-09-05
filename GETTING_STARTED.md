# 🚀 Getting Started with KartShoppe

## Zero to Running in 2 Minutes!

### Option 1: Quick Start (Recommended)
If you're starting from scratch:
```bash
./quick-start.sh
```

This single script will:
- ✅ Install SDKman
- ✅ Install Java 17
- ✅ Build the project
- ✅ Start everything with Quarkus

### Option 2: Manual Setup

#### Step 1: Install SDKman
```bash
# Install SDKman (Java version manager)
./install-sdkman.sh

# Restart terminal or run:
source ~/.sdkman/bin/sdkman-init.sh
```

#### Step 2: Install Java 17
```bash
# Install and configure Java 17
./setup-java.sh
```

#### Step 3: Start KartShoppe
```bash
# Start everything with Quarkus Dev Mode
./gradlew :quarkus-api:quarkusDev
```

## 📋 Prerequisites

### Required
- **Docker Desktop** - [Download here](https://www.docker.com/products/docker-desktop)
- **curl** - Usually pre-installed on macOS/Linux
- **8GB RAM** minimum

### Automatically Installed
- ✅ SDKman (by our scripts)
- ✅ Java 17 (by our scripts)
- ✅ Node.js (by Quinoa/Quarkus)

## 🎯 What Gets Started?

When you run the application, Quarkus automatically starts:

1. **Redpanda (Kafka)** - Event streaming platform in Docker
2. **Kafka Topics** - Automatically created
3. **Flink Inventory Job** - Real-time inventory management
4. **Quarkus API** - Backend services
5. **KartShoppe Frontend** - React UI

## 🌐 Access Points

| Service | URL |
|---------|-----|
| **KartShoppe App** | http://localhost:8080/kartshoppe/ |
| **API Health** | http://localhost:8080/q/health |
| **Dev UI** | http://localhost:8080/q/dev |
| **Inventory State** | http://localhost:8080/api/ecommerce/inventory/state |

## 🔧 Configuration Options

### Run Without Flink Job
```bash
./gradlew :quarkus-api:quarkusDev -Dflink.jobs.inventory.enabled=false
```

### Use External Kafka
```bash
# Start your own Kafka/Redpanda first, then:
./gradlew :quarkus-api:quarkusDev -Dquarkus.kafka.devservices.enabled=false
```

### Frontend Development Only
```bash
./gradlew :quarkus-api:quarkusDev \
  -Dflink.jobs.inventory.enabled=false \
  -Dquarkus.kafka.devservices.enabled=false
```

## 🐛 Troubleshooting

### Docker Issues
```bash
# Check if Docker is running
docker ps

# Start Docker on macOS
open -a Docker

# Clean up Docker
docker system prune -a
```

### Java Issues
```bash
# Check Java version
java -version

# Switch to Java 17
sdk use java 17.0.13-tem

# List installed Java versions
sdk list java | grep installed
```

### Port Conflicts
```bash
# Check what's using port 8080
lsof -i :8080

# Use a different port
./gradlew :quarkus-api:quarkusDev -Dquarkus.http.port=8090
```

### Clean Start
```bash
# Clean everything and start fresh
./gradlew clean
rm -rf quarkus-api/.quinoa
docker system prune -a
./quick-start.sh
```

## 📚 Next Steps

1. **Explore the App**: Visit http://localhost:8080/kartshoppe/
2. **Check the API**: http://localhost:8080/q/dev
3. **Monitor Services**: Watch the console output
4. **Make Changes**: Edit code and see hot reload in action!

## 🛑 Stopping the Application

Press `Ctrl+C` in the terminal where Quarkus is running. This will:
- Stop all services
- Shut down Docker containers
- Clean up resources

## 💡 Tips

- **Hot Reload**: Changes to Java, React, or properties are automatically reloaded
- **Dev UI**: Access http://localhost:8080/q/dev for Quarkus developer tools
- **Logs**: Check the console output for detailed information
- **Health**: Monitor http://localhost:8080/q/health for service status

## 📖 Documentation

- [Quarkus-Centric Guide](README-QUARKUS.md)
- [Developer Configuration](DEVELOPER_CONFIG.md)
- [Integrated Launch Details](QUARKUS_INTEGRATED_LAUNCH.md)
- [Original README](README.md)

## 🤝 Need Help?

If you encounter issues:
1. Check the troubleshooting section above
2. Look at the console output for error messages
3. Try the clean start procedure
4. Check that all prerequisites are installed

---

**Happy coding with KartShoppe! 🛒**