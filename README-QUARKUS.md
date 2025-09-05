# 🚀 KartShoppe - Quarkus-Powered Real-time E-commerce

**The Modern Way to Build Event-Driven Applications with Quarkus + Apache Flink**

## ⚡ One Command to Start Everything

```bash
./gradlew :quarkus-api:quarkusDev
```

That's it! 🎉 Quarkus Dev Services handles everything automatically.

## 🎯 What is KartShoppe?

KartShoppe is a cloud-native e-commerce platform that showcases the power of:
- **Quarkus** - Supersonic Subatomic Java with amazing developer experience
- **Apache Flink** - Real-time stream processing for inventory and recommendations
- **Apache Paimon** - Unified batch/streaming data lake
- **Redpanda** - Kafka-compatible streaming (auto-managed by Quarkus)
- **React + TypeScript** - Modern frontend served via Quinoa

## 🌟 Quarkus Dev Services Magic

When you run `./gradlew :quarkus-api:quarkusDev`, Quarkus automatically:

1. **🐳 Starts Redpanda** in a Testcontainer
2. **📊 Creates Kafka Topics** automatically
3. **⚡ Launches Flink Jobs** (configurable)
4. **🎨 Serves the Frontend** via Quinoa
5. **🔄 Enables Hot Reload** for everything
6. **📡 Configures WebSockets** for real-time updates

## 🎮 Quick Configuration

### Control What Starts

```bash
# Run without the inventory Flink job
./gradlew :quarkus-api:quarkusDev -Dflink.jobs.inventory.enabled=false

# Use external Kafka instead of dev services
./gradlew :quarkus-api:quarkusDev -Dquarkus.kafka.devservices.enabled=false

# Change Kafka port
./gradlew :quarkus-api:quarkusDev -Dquarkus.kafka.devservices.port=9092
```

### Application Properties

```properties
# Enable/disable components
%dev.quarkus.kafka.devservices.enabled=true        # Auto-start Kafka
%dev.flink.jobs.inventory.enabled=true              # Auto-start Flink job
%dev.quarkus.quinoa.enable-spa-routing=true         # Frontend SPA routing

# Configure Redpanda image
%dev.quarkus.kafka.devservices.image-name=docker.redpanda.com/redpandadata/redpanda:v24.2.4

# Topics are created automatically!
%dev.quarkus.kafka.devservices.topic-partitions.ecommerce_events=3
%dev.quarkus.kafka.devservices.topic-partitions.products=3
```

## 🔥 Developer Experience Features

### Live Coding Mode
- **Backend**: Change Java code → Automatic restart
- **Frontend**: Change React code → Instant hot reload
- **Configuration**: Update properties → Applied immediately

### Integrated Frontend
- Frontend served at: http://localhost:8080/kartshoppe/
- API at: http://localhost:8080/api/
- WebSockets work seamlessly
- No CORS issues!

### Smart Dev Services
- Kafka starts only when needed
- Topics created automatically
- Flink jobs managed by Quarkus
- Clean shutdown on Ctrl+C

## 📦 Architecture

```
┌──────────────────────────────────────────────────────┐
│                  Quarkus Dev Mode                     │
├──────────────────────────────────────────────────────┤
│                                                        │
│  ┌─────────────────────────────────────────────┐     │
│  │           Quinoa (Frontend Server)           │     │
│  │  • React + TypeScript                        │     │
│  │  • Hot Module Replacement                    │     │
│  │  • Served at /kartshoppe/                   │     │
│  └──────────────────┬──────────────────────────┘     │
│                     │                                  │
│  ┌──────────────────▼──────────────────────────┐     │
│  │              Quarkus API                     │     │
│  │  • REST Endpoints                            │     │
│  │  • WebSocket Support                         │     │
│  │  • Kafka Integration                         │     │
│  └──────────────────┬──────────────────────────┘     │
│                     │                                  │
│  ┌──────────────────▼──────────────────────────┐     │
│  │         Kafka Dev Services                   │     │
│  │  • Redpanda in Testcontainer                 │     │
│  │  • Auto Topic Creation                       │     │
│  │  • Port 19092 (configurable)                │     │
│  └──────────────────┬──────────────────────────┘     │
│                     │                                  │
│  ┌──────────────────▼──────────────────────────┐     │
│  │         Flink Jobs (Optional)                │     │
│  │  • Inventory Management                      │     │
│  │  • Recommendation Engine                     │     │
│  │  • Auto-started by Quarkus                  │     │
│  └──────────────────────────────────────────────┘     │
│                                                        │
└──────────────────────────────────────────────────────┘
```

## 🛠️ Development Workflow

### 1. Start Development
```bash
# Ensure Java 17 is active
source ~/.sdkman/bin/sdkman-init.sh && sdk use java 17.0.15-tem

# Start everything
./gradlew :quarkus-api:quarkusDev
```

### 2. Access the Application
- **Frontend**: http://localhost:8080/kartshoppe/
- **API Health**: http://localhost:8080/q/health
- **API Metrics**: http://localhost:8080/q/metrics
- **Dev UI**: http://localhost:8080/q/dev

### 3. Make Changes
- Edit Java code → Auto restart
- Edit React code → Hot reload
- Edit properties → Auto reload

### 4. Monitor Services
```bash
# Check inventory state
curl http://localhost:8080/api/ecommerce/inventory/state | jq

# Watch Kafka topics
docker exec -it <container> rpk topic list

# View logs
tail -f logs/application.log
```

## 🔧 Troubleshooting

### Kafka Not Starting?
```bash
# Check Docker is running
docker ps

# Clear Testcontainers cache
docker system prune -a

# Run with explicit port
./gradlew :quarkus-api:quarkusDev -Dquarkus.kafka.devservices.port=29092
```

### Flink Job Issues?
```bash
# Disable auto-start
./gradlew :quarkus-api:quarkusDev -Dflink.jobs.inventory.enabled=false

# Start manually later
java -cp flink-inventory/build/libs/flink-inventory.jar \
  com.ververica.composable_job.flink.inventory.InventoryManagementJob
```

### Frontend Not Loading?
```bash
# Check Quinoa is working
curl http://localhost:8080/kartshoppe/

# Clear node modules
rm -rf kartshoppe-frontend/node_modules
rm -rf quarkus-api/.quinoa

# Restart Quarkus
```

## 🚀 Production Build

```bash
# Build for production
./gradlew build

# Run production mode (no dev services)
java -jar quarkus-api/build/quarkus-app/quarkus-run.jar

# Or with Docker
docker build -f quarkus-api/src/main/docker/Dockerfile.jvm -t kartshoppe .
docker run -p 8080:8080 kartshoppe
```

## 📚 Key Technologies

### Quarkus Extensions Used
- `quarkus-messaging-kafka` - Kafka integration
- `quarkus-websockets-next` - WebSocket support
- `quarkus-kafka-streams` - Stream processing
- `quarkus-rest-jackson` - REST APIs
- `quarkiverse-quinoa` - Frontend integration
- `quarkus-smallrye-health` - Health checks

### Flink Components
- Inventory Management (Stateful Processing)
- Recommendation Engine (ML Pipeline)
- Basket Analysis (Complex Event Processing)

## 🎯 Configuration Reference

### Environment Variables
```bash
# Kafka bootstrap servers (auto-configured in dev)
KAFKA_BOOTSTRAP_SERVERS=localhost:19092

# Enable/disable features
FLINK_JOBS_INVENTORY_ENABLED=true
QUARKUS_KAFKA_DEVSERVICES_ENABLED=true

# Node version for Quinoa
QUINOA_NODE_VERSION=22.12.0
```

### Custom Properties
```properties
# Disable Flink job in dev
%dev.flink.jobs.inventory.enabled=false

# Use external Kafka
%dev.quarkus.kafka.devservices.enabled=false
%dev.kafka.bootstrap.servers=my-kafka:9092

# Change Redpanda version
%dev.quarkus.kafka.devservices.image-name=docker.redpanda.com/redpandadata/redpanda:v24.3.0
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test with `./gradlew :quarkus-api:quarkusDev`
5. Submit a pull request

## 📄 License

Apache License 2.0

---

<div align="center">
  <b>Built with ❤️ using Quarkus</b><br>
  <i>The Supersonic Subatomic Java Framework</i><br><br>
  
  Learn more at [quarkus.io](https://quarkus.io)
</div>