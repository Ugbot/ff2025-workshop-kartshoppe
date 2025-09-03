# 🛒 KartShoppe - Flink Forward Barcelona 2025 Workshop

## Real-time E-commerce Platform with Apache Flink

Welcome to the **KartShoppe** workshop at Flink Forward Barcelona 2025! This hands-on workshop demonstrates building a modern, event-driven e-commerce platform using Apache Flink, Redpanda (Kafka), and real-time stream processing.

## 🎯 Learning Outcomes

By the end of this workshop, you will:

1. **Master Event-Driven Architecture**
   - Design and implement event sourcing patterns
   - Build CQRS (Command Query Responsibility Segregation) systems
   - Handle real-time event streams at scale

2. **Apache Flink Fundamentals**
   - Create and deploy Flink jobs for stream processing
   - Implement stateful stream processing with exactly-once semantics
   - Build real-time analytics and aggregations

3. **Real-time ML & Recommendations**
   - Integrate machine learning models with streaming data
   - Build real-time recommendation engines
   - Implement basket analysis and product recommendations

4. **Production-Ready Patterns**
   - Handle inventory management with event sourcing
   - Implement WebSocket connections for real-time UI updates
   - Build resilient, scalable microservices

## 🏗️ Architecture Overview

```
┌─────────────────┐     ┌──────────────┐     ┌─────────────────┐
│   React         │────▶│  Quarkus     │────▶│   Redpanda      │
│   Frontend      │◀────│  API         │◀────│   (Kafka)       │
└─────────────────┘     └──────────────┘     └─────────────────┘
                              │                       ▲ ▼
                              ▼                 ┌─────────────────┐
                        ┌──────────────┐       │   Apache Flink  │
                        │  PostgreSQL  │       │   Jobs          │
                        └──────────────┘       └─────────────────┘
```

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Docker & Docker Compose
- Node.js 18+
- 8GB RAM minimum

### One-Command Setup

```bash
# Start all services
./start-all.sh
```

This will:
- 🟢 Start Redpanda (Kafka-compatible streaming)
- 🟢 Launch Quarkus API server
- 🟢 Deploy Flink jobs (Inventory, Recommendations)
- 🟢 Start React frontend
- 🟢 Load sample product data

### Access Points

| Service | URL | Description |
|---------|-----|-------------|
| **KartShoppe UI** | http://localhost:3000/kartshoppe/ | Main e-commerce interface |
| **Analytics Dashboard** | http://localhost:3000/kartshoppe/analytics | Real-time metrics |
| **Quarkus API** | http://localhost:8080 | REST API & WebSocket |
| **Redpanda Console** | http://localhost:8085 | Kafka topic management |

## 📚 Workshop Modules

### Module 1: Event Streaming Basics (30 min)
- Understanding event-driven architecture
- Publishing and consuming events
- Exploring Redpanda/Kafka topics

### Module 2: Stream Processing with Flink (45 min)
- Creating your first Flink job
- Processing e-commerce events
- Stateful stream processing

### Module 3: Real-time Inventory Management (45 min)
- Implementing event sourcing
- Managing distributed state
- Handling concurrent updates

### Module 4: ML-Powered Recommendations (45 min)
- Basket analysis with Flink
- Real-time recommendation generation
- A/B testing strategies

### Module 5: Production Considerations (30 min)
- Monitoring and observability
- Scaling strategies
- Error handling and recovery

## 🧩 Key Components

### Flink Jobs

1. **Inventory Management Job**
   - Tracks product availability in real-time
   - Handles concurrent purchase requests
   - Maintains exactly-once semantics

2. **Recommendation Engine**
   - Analyzes shopping patterns
   - Generates personalized recommendations
   - Updates in real-time based on user behavior

3. **Basket Analysis Job**
   - Identifies frequently bought together items
   - Calculates product affinities
   - Powers cross-selling features

### Frontend Features

- 🛍️ **Product Catalog** - Browse products with real-time inventory
- 🛒 **Shopping Cart** - Persistent cart with WebSocket updates
- 📊 **Analytics Dashboard** - Live metrics and KPIs
- 💬 **Personal Shopper Chat** - AI-powered shopping assistant
- 🎯 **Personalized Recommendations** - ML-driven product suggestions

## 🔧 Development

### Running Individual Components

```bash
# Start Redpanda only
docker-compose -f docker-compose-redpanda.yml up -d

# Run Flink Inventory Job
./gradlew flink-inventory:run

# Run Flink Recommendations Job
./gradlew flink-recommendations:run

# Start Quarkus API
./gradlew quarkus-api:quarkusDev

# Start Frontend (development mode)
cd kartshoppe-frontend
npm install
npm run dev
```

### Useful Commands

```bash
# Stop all services
./stop-all.sh

# View logs
tail -f logs/*.log

# Access Flink job manager
http://localhost:8081

# Clear all data and restart
docker-compose down -v
./start-all.sh
```

## 📝 Workshop Exercises

### Exercise 1: Add a New Event Type
Extend the platform to track product reviews in real-time.

### Exercise 2: Implement Fraud Detection
Create a Flink job to detect suspicious purchasing patterns.

### Exercise 3: Dynamic Pricing
Build a stream processing pipeline for real-time price adjustments.

### Exercise 4: Inventory Forecasting
Use historical data to predict stock-out events.

## 🤝 Contributing

This is an open-source workshop! Contributions are welcome:
- Report bugs or issues
- Submit pull requests
- Improve documentation
- Share your learning experience

## 📖 Additional Resources

- [Apache Flink Documentation](https://flink.apache.org)
- [Redpanda Documentation](https://docs.redpanda.com)
- [Workshop Slides](docs/slides.pdf)
- [Technical Architecture](docs/TECHNICAL_README.md)

## 🙏 Acknowledgments

Created with ❤️ by the Ververica team for Flink Forward Barcelona 2025.

Special thanks to all contributors and workshop participants!

## 📄 License

Apache License 2.0 - See [LICENSE](LICENSE) for details.

---

**Need Help?** 
- During the workshop: Ask your instructor!
- After the workshop: Open an issue on [GitHub](https://github.com/Ugbot/ff2025-workshop-kartshoppe)
- Join the conversation: [Flink Community Slack](https://flink.apache.org/community.html)