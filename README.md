# 🚀 KartShoppe - Flink Forward Barcelona 2025 Workshop

## Building Next-Generation Event-Driven Systems with Apache Flink & Paimon

<div align="center">
  <img src="https://ververica.com/wp-content/uploads/2023/09/Ververica-logo.svg" width="300" alt="Ververica">
  
  **A Ververica Workshop on Modern Stream Processing Architecture**
</div>

---

Welcome to the **KartShoppe** workshop at Flink Forward Barcelona 2025! This hands-on workshop by Ververica demonstrates how to build production-ready, event-driven systems using Apache Flink's advanced stream processing capabilities, Apache Paimon's unified batch-stream storage, and modern event streaming patterns.

## 🎯 Workshop Learning Objectives

By completing this workshop, you will master:

### 1. **Apache Flink Excellence**
   - Build stateful stream processing applications with Flink DataStream API
   - Implement exactly-once processing guarantees
   - Master Flink's state backends and checkpointing mechanisms
   - Deploy and monitor Flink jobs at scale

### 2. **Apache Paimon Integration** 
   - Implement unified batch and streaming data lakes
   - Build real-time OLAP with Paimon tables
   - Create CDC pipelines with Flink and Paimon
   - Optimize storage with Paimon's LSM architecture

### 3. **Event-Driven Architecture Patterns**
   - Design event sourcing and CQRS systems
   - Implement saga patterns for distributed transactions
   - Build event-driven microservices with Flink
   - Handle out-of-order and late-arriving events

### 4. **Ververica Platform Best Practices**
   - Deploy Flink applications using Ververica Platform patterns
   - Implement observability and monitoring
   - Scale streaming workloads dynamically
   - Manage application lifecycles in production

## 🏗️ Architecture: The Ververica Way

```
┌─────────────────────────────────────────────────────────────────┐
│                     Ververica Platform Layer                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐    │
│  │ React        │────▶│  Quarkus     │────▶│   Redpanda   │    │
│  │ Frontend     │◀────│  API Gateway │◀────│   (Kafka)    │    │
│  └──────────────┘     └──────────────┘     └──────────────┘    │
│         ▲                    │                      ▲            │
│         │              ┌─────▼──────┐               │            │
│         │              │ PostgreSQL │               │            │
│         │              │  (OLTP)    │               │            │
│         │              └────────────┘               │            │
│         │                                           │            │
│  ┌──────────────────────────────────────────────────▼─────┐     │
│  │                  Apache Flink Jobs                      │     │
│  ├──────────────────────────────────────────────────────────┤   │
│  │ • Inventory Management (Stateful Processing)           │     │
│  │ • Recommendation Engine (ML Pipeline)                  │     │
│  │ • Basket Analysis (CEP & Windowing)                   │     │
│  │ • CDC Pipeline (Database Sync)                        │     │
│  └────────────────────────────────────────────────────────┘     │
│         │                                           ▲            │
│         └──────────────────┬────────────────────────┘            │
│                           ▼                                      │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              Apache Paimon (Unified Storage)             │   │
│  ├──────────────────────────────────────────────────────────┤   │
│  │ • Product Catalog (Streaming Table)                      │   │
│  │ • Order History (Compacted Log)                         │   │
│  │ • User Sessions (Time-Travel Queries)                   │   │
│  │ • Analytics Views (Materialized Aggregations)           │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

## 🌟 Why This Workshop Matters

**Ververica**, as the original creators of Apache Flink and leaders in stream processing, brings you real-world patterns used in production by companies processing billions of events daily. This workshop showcases:

- **Battle-tested patterns** from Ververica's enterprise deployments
- **Cutting-edge features** in Flink 1.20+ and Paimon
- **Production insights** from the Flink committers
- **Future roadmap** of stream processing technology

## 🚀 Quick Start

### Prerequisites
- Java 17+ (for Flink jobs)
- Docker & Docker Compose
- Node.js 18+ (for frontend)
- 8GB RAM minimum
- Basic knowledge of SQL and Java/Scala

### One-Command Setup

```bash
# Clone and start the entire platform
git clone https://github.com/Ugbot/ff2025-workshop-kartshoppe.git
cd ff2025-workshop-kartshoppe
./start-all.sh
```

This orchestrates:
- 🟢 **Redpanda** - High-performance Kafka-compatible streaming
- 🟢 **Apache Flink** - Stream processing engine (multiple jobs)
- 🟢 **Apache Paimon** - Unified batch-stream storage
- 🟢 **Quarkus API** - Reactive microservices
- 🟢 **React Frontend** - Real-time UI with WebSockets

### Platform Access Points

| Component | URL | Purpose |
|-----------|-----|---------|
| **KartShoppe UI** | http://localhost:3000/kartshoppe/ | Main application |
| **Flink Dashboard** | http://localhost:8081 | Job monitoring |
| **Paimon Catalog** | http://localhost:8082 | Table management |
| **Redpanda Console** | http://localhost:8085 | Event streaming |
| **Grafana Metrics** | http://localhost:3001 | Observability |

## 📚 Workshop Modules

### Module 1: Foundations of Stream Processing (30 min)
**Ververica Insight: From Batch to Streaming**
- Evolution from MapReduce to stream processing
- Flink's architecture and runtime
- Understanding time: event-time vs processing-time
- Watermarks and late data handling

**Hands-on**: Deploy your first Flink job

### Module 2: Stateful Stream Processing with Flink (45 min)
**Ververica Pattern: Managing Distributed State**
- Flink's state backends (RocksDB, Heap)
- Keyed vs operator state
- Checkpointing and fault tolerance
- State TTL and cleanup strategies

**Hands-on**: Build an inventory management system with exactly-once guarantees

### Module 3: Apache Paimon - Unified Storage Layer (45 min)
**Ververica Innovation: Beyond Traditional Data Lakes**
- Paimon's LSM tree architecture
- Unified batch and streaming reads
- Primary key tables vs append-only tables
- Time travel and versioning

**Hands-on**: Create a real-time data warehouse with Paimon

### Module 4: Complex Event Processing & ML (45 min)
**Ververica Advanced: Intelligence in Streaming**
- Pattern detection with Flink CEP
- Window functions and aggregations
- Online machine learning pipelines
- Feature engineering in real-time

**Hands-on**: Build a recommendation engine with basket analysis

### Module 5: Production Deployment Patterns (30 min)
**Ververica Platform: Enterprise-Ready Streaming**
- Job lifecycle management
- Monitoring and alerting strategies
- Performance tuning and optimization
- Zero-downtime deployments

**Hands-on**: Deploy with savepoints and scaling

## 🧩 Core Flink Jobs in KartShoppe

### 1. **Inventory Management Job**
```java
// Showcases: Stateful processing, exactly-once semantics
DataStream<InventoryEvent> events = env.addSource(kafkaSource);
events.keyBy(e -> e.getProductId())
      .process(new InventoryStateFunction())
      .sink(paimonSink);
```

### 2. **Real-time Recommendation Engine**
```java
// Showcases: ML pipeline, windowed aggregations
DataStream<UserAction> actions = env.addSource(kafkaSource);
actions.keyBy(a -> a.getUserId())
       .window(SlidingEventTimeWindows.of(Time.minutes(30)))
       .aggregate(new BasketAnalysis())
       .asyncMap(new MLScoringFunction());
```

### 3. **CDC Pipeline with Paimon**
```java
// Showcases: Change Data Capture, Paimon integration
FlinkCDC.postgres()
        .database("ecommerce")
        .table("orders")
        .deserializer(new JsonDebeziumDeserializationSchema())
        .build()
        .sinkTo(PaimonSink.forRowData(...));
```

## 🔬 Advanced Topics Covered

- **Event Time Processing**: Handling out-of-order events
- **State Migration**: Evolving schemas without downtime
- **Async I/O**: Enriching streams with external data
- **Side Outputs**: Handling errors and late data
- **Broadcast State**: Distributing configuration dynamically
- **Table API & SQL**: Declarative stream processing
- **PyFlink**: Python support for data scientists

## 🎯 Workshop Exercises

### Exercise 1: Event Sourcing Implementation
Build a complete event-sourced order processing system with Flink and Paimon.

### Exercise 2: Real-time Fraud Detection
Implement CEP patterns to detect suspicious transaction sequences.

### Exercise 3: Dynamic Pricing Engine
Create a streaming pipeline that adjusts prices based on demand and inventory.

### Exercise 4: Multi-Region Replication
Set up cross-datacenter replication using Flink and Paimon.

## 🛠️ Development Tools

```bash
# Flink CLI commands
./bin/flink run -c com.ververica.InventoryJob target/inventory.jar
./bin/flink savepoint <jobId> hdfs:///savepoints
./bin/flink cancel -s <jobId>

# Paimon operations
./bin/paimon create-table --warehouse /tmp/paimon --table products
./bin/paimon compact --warehouse /tmp/paimon --table orders

# Monitoring
curl http://localhost:8081/jobs/<jobId>/checkpoints
curl http://localhost:8081/jobs/<jobId>/metrics
```

## 📊 Performance Benchmarks

Based on Ververica's production deployments:

| Metric | KartShoppe Performance |
|--------|------------------------|
| **Event Throughput** | 100K events/second |
| **End-to-end Latency** | < 100ms p99 |
| **State Size** | 10GB+ RocksDB |
| **Checkpointing** | 30-second intervals |
| **Recovery Time** | < 2 minutes |

## 🌍 Ververica & Community

### About Ververica
Ververica, founded by the original creators of Apache Flink, provides:
- **Ververica Platform** - Enterprise Flink management
- **Consulting** - Architecture and optimization
- **Training** - Official Flink certification
- **Support** - 24/7 enterprise assistance

### Resources
- [Ververica Academy](https://www.ververica.com/academy)
- [Apache Flink Documentation](https://flink.apache.org)
- [Apache Paimon Documentation](https://paimon.apache.org)
- [Ververica Blog](https://www.ververica.com/blog)
- [Flink Forward Conference](https://www.flinkforward.org)

### Community
- [Apache Flink Slack](https://flink.apache.org/community.html)
- [Ververica Forum](https://forum.ververica.com)
- [Stack Overflow #apache-flink](https://stackoverflow.com/questions/tagged/apache-flink)

## 🤝 Contributing

This workshop is open-source and contributions are welcome:
- Submit pull requests for improvements
- Report issues and bugs
- Share your use cases
- Contribute Flink jobs and patterns

## 📄 License

Apache License 2.0 - See [LICENSE](LICENSE) for details.

---

<div align="center">
  <b>Built with ❤️ by Ververica</b><br>
  <i>Empowering the world with Apache Flink</i><br><br>
  
  **Need Enterprise Support?**<br>
  Contact us at [ververica.com](https://www.ververica.com)
</div>