# 🚀 Flink Ecosystem - Building Pipelines for Real-Time Data Lakes

## Flink Forward Barcelona 2025 | 2-Day Intensive Workshop

<div align="center">
  <img src="https://ververica.com/wp-content/uploads/2023/09/Ververica-logo.svg" width="300" alt="Ververica">

  **Advanced Apache Flink Training leveraging Ververica Technology**
</div>

---

Welcome to **Building Pipelines for Real-Time Data Lakes**! This intensive, 2-day face-to-face program is designed for Apache Flink users with 1-2 years of experience who want to take their skills to the intermediate level. We'll delve into advanced Flink concepts and techniques, empowering you to build and deploy highly scalable and efficient real-time data processing pipelines.

## 👥 Target Audience

**Intermediate to Advanced** | 1-2 years of experience working with:
- Data lakes
- Stream processing
- Apache Flink

## 🎯 Why This Workshop?

Flink helps unlock the full potential of data lakes by providing the necessary stream and batch processing capabilities to transform raw data into actionable insights. This course will:

**Focus on the relationship between data lakes and how they are rooted in Flink's ability to process and analyze large-scale data that often resides in data lakes.**

---

## 📚 What Will This Workshop Cover?

### 1. **Data Lakes as Sources**
Easily integrate data from cost-effective distributed storage systems in varying open source formats like **Paimon** into your Flink pipelines.

### 2. **Flink for Real-Time Processing**
Utilize Data Lakes as the input of streaming and batch jobs, bridging historical and real-time data.

### 3. **Real-Time Data Ingestion**
Automate all parts of your data processing pipeline with real-time data ingestion, enabling:
- Real-time decision making
- Model training
- Inference

### 4. **Long-Term Storage and Retrieval**
Utilize Data Lakes for storage of intermediate processed results (e.g., feature engineering) with easy ingestion for downstream applications like retraining of machine learning models.

### 5. **Unified Data Processing**
Flink provides a unified framework for both stream and batch processing, important for managing and processing data at scale in Data Lakes. Flink provides consistency across your entire data pipeline with the scale and optimizations only Flink can provide.

---

## 🔧 How the Flink Ecosystem Technologies Fit Into the Workshop

**Flink CDC, Paimon, and Fluss** are all integral parts of the Flink ecosystem that allow us to integrate large-scale Data Lakes with the real-time data processing of Flink. We use **real-world use cases** and **hands-on exercises** to demonstrate how Flink and Data Lakes work together seamlessly.

### Key Technologies

**🔄 Flink CDC + Flink SQL**
- Automate data cleansing for real-time feature engineering in machine learning models
- Retrieve raw data or store feature engineering results in Paimon
- **NEW:** PostgreSQL CDC → Paimon pipeline included (see [FLINK-CDC-PAIMON-GUIDE.md](./FLINK-CDC-PAIMON-GUIDE.md))

**📦 Apache Paimon**
- Cost-effective, large-scale storage ready for real-time and batch processing
- Combine data from Data Lakes and Kafka sources to create real-time machine learning pipelines with both historical and real-time model training
- **NEW:** Historical pattern training data + CDC tables in unified lakehouse

**⚡ Apache Fluss**
- Supports low-latency stream processing
- Ideal for storing and retrieving data with real-time analytics
- High-performance event processing for data continuously ingested and stored in a Data Lake

**🎯 CEP (Complex Event Processing)**
- Power real-time pipelines using an optimized approach of CEP and model inference
- Backed by the scale and power of Flink

## 🏗️ Real-Time Data Lake Architecture with KartShoppe

```
┌─────────────────────────────────────────────────────────────────┐
│               REAL-TIME DATA LAKE PIPELINE                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              DATA SOURCES                                 │   │
│  ├──────────────────────────────────────────────────────────┤   │
│  │  • Historical Data (Paimon)                              │   │
│  │  • Real-time Streams (Kafka/Redpanda)                    │   │
│  │  • CDC from PostgreSQL (Flink CDC)                       │   │
│  │  • Low-latency Events (Fluss)                            │   │
│  └────────────┬───────────────┬─────────────┬────────────────┘   │
│               │               │             │                    │
│               ▼               ▼             ▼                    │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │         APACHE FLINK PROCESSING LAYER                    │    │
│  ├─────────────────────────────────────────────────────────┤    │
│  │                                                          │    │
│  │  📊 Flink SQL & Table API                               │    │
│  │    • Data cleansing and validation                      │    │
│  │    • Feature engineering for ML                         │    │
│  │    • Real-time aggregations                             │    │
│  │                                                          │    │
│  │  🔄 Stateful Stream Processing                          │    │
│  │    • Session Windows (user behavior)                    │    │
│  │    • Keyed State (inventory tracking)                   │    │
│  │    • Timers (timeout detection)                         │    │
│  │                                                          │    │
│  │  🎯 Complex Event Processing (CEP)                       │    │
│  │    • Cart abandonment patterns                          │    │
│  │    • Fraud detection sequences                          │    │
│  │    • User journey tracking                              │    │
│  │                                                          │    │
│  │  🤖 ML Model Training & Inference                        │    │
│  │    • Broadcast State (model distribution)               │    │
│  │    • Real-time predictions                              │    │
│  │    • Historical + real-time features                    │    │
│  │                                                          │    │
│  └────────┬──────────────┬──────────────┬────────────────┘    │
│           │              │              │                      │
│           ▼              ▼              ▼                      │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │          DATA LAKE STORAGE (Apache Paimon)               │    │
│  ├─────────────────────────────────────────────────────────┤    │
│  │  • Feature Store (engineered features)                  │    │
│  │  • Model Training Data (historical + real-time)         │    │
│  │  • Processed Results (batch + streaming)                │    │
│  │  • Time-travel enabled (versioned data)                 │    │
│  └────────────────────────────────────────────────────────┘     │
│           │                                                      │
│           ▼                                                      │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │          DOWNSTREAM APPLICATIONS                         │    │
│  ├─────────────────────────────────────────────────────────┤    │
│  │  • React Frontend (real-time UI)                        │    │
│  │  • Quarkus API (serving layer)                          │    │
│  │  • ML Model Retraining (batch jobs)                     │    │
│  │  • Analytics & Reporting                                │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🎓 NEW: Pattern Learning Modules

This workshop now includes **isolated pattern learning modules** - learn Flink patterns one at a time before combining them!

### Inventory Management Patterns (Foundational)
**Location:** `flink-inventory/patterns/`

| Pattern | What You'll Learn | Time |
|---------|------------------|------|
| **01. Hybrid Source** | Bootstrap from files → Kafka streaming | 45 min |
| **02. Keyed State** | Per-key fault-tolerant state management | 60 min |
| **03. Timers** | Timeout detection & scheduled processing | 60 min |
| **04. Side Outputs** | Multi-way event routing (60% faster than filters!) | 45 min |

**Quick start a pattern:**
```bash
# Run Pattern 01: Hybrid Source
./gradlew :flink-inventory:run -PmainClass=com.ververica.composable_job.flink.inventory.patterns.hybrid_source.HybridSourceExample
```

See [`flink-inventory/patterns/README.md`](flink-inventory/src/main/java/com/ververica/composable_job/flink/inventory/patterns/README.md) for the complete learning guide.

---

### Recommendation Patterns (Advanced)
**Location:** `flink-recommendations/patterns/`

| Pattern | What You'll Learn | Time |
|---------|------------------|------|
| **01. Session Windows** | Group events by user sessions with inactivity gaps | 60-90 min |
| **02. Broadcast State** | Distribute ML models to all tasks for real-time inference | 60-90 min |
| **03. CEP (Complex Event Processing)** | Detect sequential patterns (cart abandonment, fraud) | 90-120 min |

**Quick start a pattern:**
```bash
# Run Pattern 01: Session Windows
./gradlew :flink-recommendations:run -PmainClass=com.ververica.composable_job.flink.recommendations.patterns.session_windows.SessionWindowExample

# Run Pattern 02: Broadcast State
./gradlew :flink-recommendations:run -PmainClass=com.ververica.composable_job.flink.recommendations.patterns.broadcast_state.BroadcastStateExample

# Run Pattern 03: CEP Cart Abandonment
./gradlew :flink-recommendations:run -PmainClass=com.ververica.composable_job.flink.recommendations.patterns.cep.CEPCartAbandonmentExample
```

See [`flink-recommendations/patterns/README.md`](flink-recommendations/src/main/java/com/ververica/composable_job/flink/recommendations/patterns/README.md) for the complete learning guide.

---

**Each module includes:**
- ✅ Standalone runnable example
- ✅ Comprehensive README (600-1,400 lines) with visual diagrams
- ✅ 3 hands-on exercises with detailed solutions
- ✅ Production tips & common pitfalls
- ✅ 5 quiz questions with explanations
- ✅ Real-world use cases (e-commerce, fraud detection, IoT)

## 🚀 Quick Start

### Prerequisites
- **Java 11** (for building Flink jobs - Flink 1.20 requirement)
- **Java 17+** (runs in Docker for Quarkus API)
- Docker & Docker Compose
- Node.js 18+ (for frontend)
- 8GB RAM minimum
- Basic knowledge of SQL and Java

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
| **PostgreSQL** | localhost:5432 | Operational database (CDC source) |
| **Grafana Metrics** | http://localhost:3001 | Observability |

## 🎯 Flink Job Implementations

This demo includes **3 job variants** for different use cases:

### 1. **BasketAnalysisJobRefactored** (Standard Job)
**Recommended for:** First-time users, understanding the core patterns

- Mines basket patterns from live shopping events
- Writes patterns to both Kafka and Paimon
- Uses Keyed State, Timers, and Broadcast State
- **Start:** `./start-basket-job.sh`

**Use when:** Learning the architecture or running with fresh data

---

### 2. **BasketAnalysisJobWithPretraining** (Hybrid Source Job)
**Recommended for:** Production deployments, best recommendation quality

- Pre-loads 5000 historical patterns from Paimon (warm start)
- Continues mining new patterns from live traffic
- Provides immediate high-quality recommendations
- **Start:** `./start-basket-job-with-pretraining.sh`

**Use when:** You want the best cold-start performance (runs historical data generator first if needed)

---

### 3. **HistoricalBasketDataGenerator** (Training Data Generator)
**Recommended for:** Creating sample training data

- Generates 5000 realistic basket patterns
- Covers 4 product categories (electronics, fashion, home_kitchen, sports)
- 90-day temporal distribution
- **Start:** `./init-paimon-training-data.sh`

**Use when:** Setting up the demo for the first time or testing the hybrid source

---

**Quick Decision Guide:**
- **New to the project?** → Start with `BasketAnalysisJobRefactored` (#1)
- **Want best recommendations?** → Use `BasketAnalysisJobWithPretraining` (#2)
- **Need training data?** → Run `HistoricalBasketDataGenerator` (#3) first

---

## 🔄 NEW: Flink CDC → Paimon Pipeline

This demo now includes a complete **Change Data Capture (CDC)** pipeline that synchronizes PostgreSQL tables into Paimon in real-time!

### Architecture

```
PostgreSQL (Operational)     Flink CDC             Paimon (Lakehouse)      Flink Jobs
┌─────────────────┐          ┌────────────┐        ┌──────────────┐      ┌──────────────┐
│  products       │          │ Logical    │        │ lakehouse.   │      │ Enriched     │
│  customers      │──WAL────▶│ Replication│───────▶│   products   │─────▶│ Recommenda-  │
│  orders         │          │ Slot       │        │   customers  │      │ tions        │
│  inventory      │          │            │        │   orders     │      │              │
└─────────────────┘          └────────────┘        └──────────────┘      └──────────────┘
  Millisecond                Sub-second             Unified lakehouse     Real-time ML
  latency writes             CDC capture            with time-travel      with fresh data
```

### Quick Start CDC

```bash
# 1. Start PostgreSQL with CDC enabled
docker compose up -d postgres

# 2. Verify e-commerce tables created
docker exec -it postgres-cdc psql -U postgres -d ecommerce -c "\dt"

# 3. Follow the complete guide
open FLINK-CDC-PAIMON-GUIDE.md
```

### What's Included

- ✅ **PostgreSQL 15** with logical replication enabled
- ✅ **Sample E-Commerce Schema**: products, customers, orders, inventory (44 sample products)
- ✅ **Comprehensive Guide**: [FLINK-CDC-PAIMON-GUIDE.md](./FLINK-CDC-PAIMON-GUIDE.md)
- ✅ **Multiple Sync Modes**: Single table, multi-table, regex, entire database
- ✅ **Real-time Updates**: INSERT/UPDATE/DELETE captured in milliseconds
- ✅ **Schema Evolution**: Automatic DDL tracking

### Use Cases Enabled

1. **Product Catalog Sync**: Always-fresh product data for recommendations
2. **Inventory Tracking**: Real-time stock levels in data lake
3. **Customer 360**: Unified customer view across operational + analytical
4. **Order Analytics**: Instant order insights without batch ETL

### Key Benefits

| Without CDC | With Flink CDC → Paimon |
|-------------|------------------------|
| Batch ETL (hours delay) | Real-time (sub-second) |
| Complex Airflow DAGs | Single Flink job |
| Schema drift issues | Auto schema evolution |
| Separate operational/analytical DBs | Unified lakehouse |

📖 **Full Documentation**: [FLINK-CDC-PAIMON-GUIDE.md](./FLINK-CDC-PAIMON-GUIDE.md)

---

## 📚 2-Day Workshop Schedule

### Day 1: Foundations & Data Lake Integration

#### Session 1: Data Lakes as Flink Sources (90 min)
**Topics:**
- Integrating Paimon with Flink pipelines
- Reading from cost-effective distributed storage
- Supporting multiple open source formats
- Bootstrapping state from historical data

**Hands-on Lab:** Configure Paimon as source, initialize inventory state

---

#### Session 2: Real-Time Data Ingestion (90 min)
**Topics:**
- Combining batch and stream processing
- Flink CDC for database synchronization
- Kafka + Paimon integration
- Automating data cleansing pipelines

**Hands-on Lab:** Set up Flink CDC, stream updates to Paimon

---

#### Session 3: Flink SQL & Feature Engineering (120 min)
**Topics:**
- Flink SQL and Table API fundamentals
- Real-time feature engineering for ML
- Data transformations and aggregations
- Storing features in Paimon

**Hands-on Lab:** Write Flink SQL for feature extraction, calculate rolling aggregates

---

### Day 2: Advanced Patterns & ML Pipelines

#### Session 4: Unified Stream & Batch Processing (90 min)
**Topics:**
- Flink's unified processing model
- Session Windows for user behavior analysis
- Stateful processing (Keyed State, Timers, Side Outputs)
- Pattern learning modules (hands-on exercises)

**Hands-on Lab:** Session windows, keyed state inventory tracking, side outputs

---

#### Session 5: Real-Time ML Pipelines (120 min)
**Topics:**
- Broadcast State for model distribution
- Combining historical + real-time features
- Model inference in Flink
- Feature store with Paimon

**Hands-on Lab:** Distribute ML models, build recommendation engine

---

#### Session 6: Complex Event Processing (90 min)
**Topics:**
- CEP patterns for cart abandonment
- Fraud detection with sequential patterns
- Optimized CEP + model inference
- Integration with Fluss for low-latency

**Hands-on Lab:** Detect cart abandonment, build fraud detection pipeline

---

#### Session 7: Production Deployment (60 min)
**Topics:**
- Long-term storage strategies with Paimon
- Retrieval patterns for downstream apps
- Performance optimization
- Monitoring and observability

**Hands-on Lab:** Deploy complete pipeline, configure checkpointing

## 🛍️ KartShoppe: The Demo Application

**KartShoppe** is a realistic e-commerce platform that demonstrates real-time data lake patterns using the Flink ecosystem. It's your hands-on laboratory for learning how to build production-grade streaming pipelines.

### Real-World Use Cases Demonstrated

#### 1. **Real-Time Inventory Management** 📦
**Business Problem:** Track product stock levels across warehouses in real-time to prevent overselling.

**What You'll Build:**
- Stream inventory updates from PostgreSQL (Flink CDC)
- Store historical inventory snapshots in Paimon
- Process real-time stock changes with keyed state
- Detect low-stock/out-of-stock conditions with timers
- Route alerts using side outputs

**Data Lake Integration:**
- Bootstrap inventory from Paimon (historical data)
- Stream live updates from Kafka (real-time)
- Write processed results back to Paimon (feature store)

**Technologies:** Flink CDC, Keyed State, Timers, Side Outputs, Paimon

---

#### 2. **Shopping Session Analysis** 🛒
**Business Problem:** Understand user behavior by analyzing complete shopping sessions to improve conversion rates.

**What You'll Build:**
- Track user sessions with event-time session windows
- Classify sessions (PURCHASE, ABANDONED_CART, BROWSER, QUICK_VISIT)
- Calculate session metrics (duration, items viewed, cart value)
- Store session summaries in Paimon for ML training

**Data Lake Integration:**
- Read historical sessions from Paimon
- Process real-time events from Kafka
- Write session features to Paimon (for model retraining)

**Technologies:** Session Windows, Paimon, Flink SQL

---

#### 3. **Real-Time Recommendation Engine** 🤖
**Business Problem:** Provide personalized product recommendations using ML models updated hourly.

**What You'll Build:**
- Distribute ML models to all Flink tasks (broadcast state)
- Combine historical features (Paimon) + real-time events (Kafka)
- Run inference in real-time (<100ms latency)
- Store recommendations in Fluss for instant retrieval

**Data Lake Integration:**
- Load trained models from Paimon
- Read user profiles and product features from Paimon
- Write inference results to Fluss (low-latency storage)
- Archive predictions to Paimon (for model evaluation)

**Technologies:** Broadcast State, Paimon, Fluss, Flink SQL

---

#### 4. **Cart Abandonment Detection** ⚠️
**Business Problem:** Identify users who add items to cart but don't purchase, triggering recovery emails.

**What You'll Build:**
- Detect ADD_TO_CART → (no PURCHASE) patterns with CEP
- Calculate abandoned cart value
- Trigger alerts for high-value abandonment ($100+)
- Track recovery rates

**Data Lake Integration:**
- Store abandonment patterns in Paimon
- Analyze historical abandonment trends
- Feed data to ML models for predicting likelihood to convert

**Technologies:** Flink CEP, Paimon

---

#### 5. **Feature Engineering for ML** 🧮
**Business Problem:** Create real-time features for machine learning models (recommendations, pricing, fraud detection).

**What You'll Build:**
- Calculate rolling aggregates (user's avg cart value, purchase frequency)
- Compute product popularity metrics
- Generate user behavior features
- Store in Paimon feature store

**Data Lake Integration:**
- Batch feature calculation from historical Paimon data
- Stream real-time feature updates
- Unified feature store (batch + stream) in Paimon
- ML models read features for training/inference

**Technologies:** Flink SQL, Table API, Paimon

---

#### 6. **End-to-End ML Pipeline** 🔄
**Business Problem:** Train and deploy ML models using both historical and real-time data.

**What You'll Build:**
- Batch training on historical data (Paimon)
- Real-time feature updates (streaming)
- Model distribution (broadcast state)
- Online inference (<50ms)
- Model monitoring and retraining

**Data Lake Integration:**
```
Historical Data (Paimon) ─┐
                          ├─→ Feature Engineering (Flink SQL) ─→ Training Data (Paimon)
Real-time Events (Kafka) ─┘                                              │
                                                                          ▼
                                                                    Model Training
                                                                          │
                                                                          ▼
Inference Requests ─→ Flink (Broadcast Model) ─→ Predictions ─→ Fluss/Paimon
```

**Technologies:** Flink SQL, Broadcast State, Paimon, Fluss

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