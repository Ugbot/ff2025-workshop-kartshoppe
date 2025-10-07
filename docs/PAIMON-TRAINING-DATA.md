# Paimon Training Data Setup

## Overview

This guide explains how to set up Apache Paimon for storing historical basket patterns that can be used for ML model training.

## Architecture

### Data Flow Overview

```
Historical Data Generator
    │
    ├─ Generate 5000 realistic patterns
    ├─ Categories: electronics, fashion, home_kitchen, sports
    ├─ 90-day temporal distribution
    │
    ▼
Paimon Table: basket_analysis.basket_patterns
    │
    ├─ Schema: pattern_id, antecedents, consequent, metrics
    ├─ Primary key: pattern_id
    ├─ Features: time-travel, auto-compaction, changelog
    │
    ▼
ML Training Pipeline
    │
    ├─ Read historical patterns
    ├─ Train recommendation models
    ├─ Compare with real-time mining
```

### Hybrid Source Architecture (WITH Pre-training)

```
┌─────────────────────────────────────────────────────────────┐
│  BasketAnalysisJobWithPretraining                           │
│                                                              │
│  Phase 1: BOUNDED READ (Historical)                         │
│  ┌──────────────────────────────────┐                       │
│  │  Paimon Table                    │                       │
│  │  basket_analysis.basket_patterns │                       │
│  │  (5000 patterns)                 │                       │
│  └──────────────┬───────────────────┘                       │
│                 │                                            │
│                 │ Read all rows (Table API)                 │
│                 ▼                                            │
│  ┌──────────────────────────────────┐                       │
│  │  Convert to BasketPattern        │                       │
│  │  (DataStream)                    │                       │
│  └──────────────┬───────────────────┘                       │
│                 │                                            │
│  Phase 2: UNBOUNDED STREAM (Live)                           │
│  ┌──────────────────────────────────┐                       │
│  │  Kafka Topic                     │                       │
│  │  basket-patterns                 │                       │
│  │  (new patterns from mining)      │                       │
│  └──────────────┬───────────────────┘                       │
│                 │                                            │
│                 │ Stream continuously                        │
│                 ▼                                            │
│  ┌──────────────────────────────────┐                       │
│  │  Parse JSON to BasketPattern     │                       │
│  └──────────────┬───────────────────┘                       │
│                 │                                            │
│  ┌──────────────▼───────────────────┐                       │
│  │  UNION                           │                       │
│  │  Historical + Live Patterns      │                       │
│  └──────────────┬───────────────────┘                       │
│                 │                                            │
│                 │ Broadcast to all tasks                     │
│                 ▼                                            │
│  ┌──────────────────────────────────┐                       │
│  │  Broadcast State                 │                       │
│  │  (5000+ patterns in memory)      │                       │
│  │  - Pattern matching              │                       │
│  │  - Recommendation generation     │                       │
│  └──────────────┬───────────────────┘                       │
│                 │                                            │
│                 ▼                                            │
│  ┌──────────────────────────────────┐                       │
│  │  Recommendations                 │                       │
│  │  (immediate, high quality)       │                       │
│  └──────────────────────────────────┘                       │
└─────────────────────────────────────────────────────────────┘
```

### Cold Start vs Warm Start Comparison

| Mode | Job | Historical Patterns | First Event Recs | Quality |
|------|-----|---------------------|------------------|---------|
| **Cold Start** | `start-basket-job.sh` | ❌ No | ❌ Poor (0 patterns) | Low initially |
| **Warm Start** | `start-basket-job-with-pretraining.sh` | ✅ Yes (5000) | ✅ Excellent | High immediately |

**Performance Impact:**
- **Without pre-training:** Recommendations start poor, improve over hours/days as patterns accumulate
- **With pre-training:** Recommendations excellent from first event, continue improving with live data

## Quick Start

### 1. Initialize Paimon Table with Training Data

```bash
# Generate 5000 historical patterns
./init-paimon-training-data.sh
```

**What this does:**
- Creates Paimon catalog at `/tmp/paimon`
- Creates database `basket_analysis`
- Creates table `basket_patterns`
- Generates 5000 realistic basket patterns
- Distributes patterns over 90 days

**Expected output:**
```
🏗️  Initializing Paimon Table & Historical Training Data

Configuration:
  Paimon Warehouse: /tmp/paimon
  Number of Patterns: 5000

✓ Build successful
✓ Paimon table created successfully

Table location:
  /tmp/paimon/basket_analysis.db/basket_patterns

✅ Paimon Initialization Complete!
```

### 2. Start Job WITH PRE-TRAINING (Recommended)

```bash
# Start job that loads historical patterns first
./start-basket-job-with-pretraining.sh
```

**What this does:**
- **Phase 1:** Loads 5000 historical patterns from Paimon (bounded read)
- **Phase 2:** Streams new patterns from Kafka (unbounded stream)
- Uses hybrid source for cold-start performance
- Recommendations work immediately from first shopping event!

**Expected output:**
```
🎯 MODE: WARM START (with historical patterns)
   Historical patterns will be loaded from Paimon
   Recommendations will work from first shopping event!

✅ Found Paimon table: basket_analysis.basket_patterns
📚 Loading 5000 historical patterns...
✓ Patterns loaded into broadcast state
🚀 Ready for real-time recommendations!
```

### 2b. Alternative: Start Job WITHOUT PRE-TRAINING

```bash
# Set Paimon warehouse path
export PAIMON_WAREHOUSE=/tmp/paimon

# Start the standard basket analysis job
./start-basket-job.sh
```

**What this does:**
- Runs BasketAnalysisJobRefactored (standard version)
- Writes real-time patterns to Kafka (monitoring)
- Writes patterns to Paimon (historical storage)
- **Does NOT pre-load patterns** - cold start performance

**When to use:**
- Testing pattern mining algorithms
- Don't need immediate recommendations
- Want to observe pattern accumulation from scratch

### 3. Query the Data

Using Flink SQL Client:

```sql
-- Create Paimon catalog
CREATE CATALOG paimon_catalog WITH (
  'type' = 'paimon',
  'warehouse' = '/tmp/paimon'
);

USE CATALOG paimon_catalog;

-- View recent patterns
SELECT
  pattern_id,
  ARRAY_JOIN(antecedents, ', ') as antecedents,
  consequent,
  confidence,
  support,
  lift,
  category,
  created_at
FROM basket_analysis.basket_patterns
WHERE confidence > 0.5
ORDER BY created_at DESC
LIMIT 10;

-- Aggregate by category
SELECT
  category,
  COUNT(*) as pattern_count,
  AVG(confidence) as avg_confidence,
  AVG(support) as avg_support,
  AVG(lift) as avg_lift
FROM basket_analysis.basket_patterns
GROUP BY category
ORDER BY pattern_count DESC;

-- Time-travel: Query patterns from 30 days ago
SELECT COUNT(*) as pattern_count
FROM basket_analysis.basket_patterns
FOR SYSTEM_TIME AS OF TIMESTAMP '2024-12-01 00:00:00';
```

## Table Schema

```sql
CREATE TABLE basket_analysis.basket_patterns (
  pattern_id STRING,              -- Unique ID: antecedents_consequent
  antecedents ARRAY<STRING>,      -- Product IDs in pattern [laptop, mouse]
  consequent STRING,              -- Recommended product ID
  support DOUBLE,                 -- P(antecedents AND consequent) [0.0-1.0]
  confidence DOUBLE,              -- P(consequent | antecedents) [0.0-1.0]
  lift DOUBLE,                    -- confidence / P(consequent) [>1.0 is good]
  category STRING,                -- Product category
  user_id STRING,                 -- User who generated pattern (optional)
  session_id STRING,              -- Session ID (optional)
  created_at TIMESTAMP(3),        -- When pattern was mined
  event_time TIMESTAMP(3),        -- Event time for time-travel
  PRIMARY KEY (pattern_id) NOT ENFORCED
) WITH (
  'bucket' = '4',
  'changelog-producer' = 'input',
  'snapshot.time-retained' = '7d',
  'snapshot.num-retained.min' = '10',
  'snapshot.num-retained.max' = '100',
  'compaction.min.file-num' = '5',
  'compaction.max.file-num' = '50'
);
```

## Generated Pattern Categories

### Electronics (40% of patterns)

**Common associations:**
- `laptop` → `mouse` (confidence: 0.75, support: 0.15, lift: 2.1)
- `laptop` → `keyboard` (confidence: 0.68, support: 0.12, lift: 1.9)
- `phone` → `charger` (confidence: 0.85, support: 0.20, lift: 2.8)
- `camera` → `sd_card` (confidence: 0.88, support: 0.14, lift: 2.9)

### Fashion (25% of patterns)

**Common associations:**
- `shirt` → `pants` (confidence: 0.72, support: 0.25, lift: 2.0)
- `dress` → `shoes` (confidence: 0.76, support: 0.18, lift: 2.3)
- `suit` → `tie` (confidence: 0.83, support: 0.16, lift: 2.6)

### Home & Kitchen (20% of patterns)

**Common associations:**
- `coffee_maker` → `coffee_beans` (confidence: 0.89, support: 0.22, lift: 2.7)
- `coffee_maker` → `filters` (confidence: 0.81, support: 0.19, lift: 2.5)
- `knife_set` → `cutting_board` (confidence: 0.79, support: 0.17, lift: 2.4)

### Sports (15% of patterns)

**Common associations:**
- `bicycle` → `helmet` (confidence: 0.87, support: 0.23, lift: 2.8)
- `yoga_mat` → `yoga_blocks` (confidence: 0.67, support: 0.14, lift: 2.0)
- `tennis_racket` → `tennis_balls` (confidence: 0.89, support: 0.22, lift: 2.9)

## Metrics Explained

### Confidence
- **Definition:** P(consequent | antecedents)
- **Example:** 85% of users who bought laptop also bought mouse
- **Range:** 0.3 - 0.95 in generated data
- **Use:** Strength of recommendation

### Support
- **Definition:** P(antecedents AND consequent)
- **Example:** 10% of all baskets contain both laptop and mouse
- **Range:** 0.01 - 0.3 in generated data
- **Use:** Filter rare patterns

### Lift
- **Definition:** confidence / P(consequent)
- **Interpretation:**
  - Lift > 1.0: Positive correlation (buy together more than random)
  - Lift = 1.0: No correlation (independent)
  - Lift < 1.0: Negative correlation (avoid together)
- **Range:** 1.1 - 3.0 in generated data
- **Use:** Prioritize strong associations

## Configuration Options

### Environment Variables

```bash
# Paimon warehouse location (default: /tmp/paimon)
export PAIMON_WAREHOUSE=/custom/path/paimon

# Number of patterns to generate (default: 5000)
export NUM_PATTERNS=10000

# Kafka bootstrap servers (default: localhost:19092)
export KAFKA_BOOTSTRAP_SERVERS=localhost:19092

# Flink parallelism (default: 2)
export PARALLELISM=4

# Checkpoint interval in ms (default: 30000)
export CHECKPOINT_INTERVAL_MS=60000
```

### Customize Pattern Generation

Edit `HistoricalBasketDataGenerator.java`:

```java
// Change number of patterns
private static final int NUM_PATTERNS = 10000;

// Change time range
private static final int DAYS_HISTORY = 180; // 6 months

// Adjust category weights in selectRandomCategory()
if (rand < 0.50) return "electronics";  // 50% instead of 40%
```

## Use Cases

### 1. Train Baseline Recommendation Model

```python
# Read from Paimon using PyFlink or Pandas
from pyflink.table import EnvironmentSettings, TableEnvironment

t_env = TableEnvironment.create(EnvironmentSettings.in_batch_mode())

# Create Paimon catalog
t_env.execute_sql("""
    CREATE CATALOG paimon_catalog WITH (
      'type' = 'paimon',
      'warehouse' = '/tmp/paimon'
    )
""")

t_env.use_catalog('paimon_catalog')

# Read patterns
patterns_df = t_env.sql_query("""
    SELECT * FROM basket_analysis.basket_patterns
    WHERE confidence > 0.5 AND support > 0.05
""").to_pandas()

# Train model
from sklearn.ensemble import RandomForestClassifier
# ... training logic
```

### 2. Compare Real-time vs Historical Patterns

```sql
-- Historical patterns (from generator)
SELECT
  category,
  AVG(confidence) as historical_confidence
FROM basket_analysis.basket_patterns
WHERE user_id LIKE 'hist_user_%'
GROUP BY category;

-- Live patterns (from real traffic)
SELECT
  category,
  AVG(confidence) as live_confidence
FROM basket_analysis.basket_patterns
WHERE user_id NOT LIKE 'hist_user_%'
GROUP BY category;
```

### 3. Time-Travel Analysis

```sql
-- Patterns mined 7 days ago
SELECT * FROM basket_analysis.basket_patterns
FOR SYSTEM_TIME AS OF TIMESTAMP '2024-12-25 00:00:00';

-- Compare pattern evolution over time
WITH snapshots AS (
  SELECT 'week_1' as period, COUNT(*) as cnt
  FROM basket_analysis.basket_patterns
  FOR SYSTEM_TIME AS OF TIMESTAMP '2024-12-01 00:00:00'

  UNION ALL

  SELECT 'week_2', COUNT(*)
  FROM basket_analysis.basket_patterns
  FOR SYSTEM_TIME AS OF TIMESTAMP '2024-12-08 00:00:00'
)
SELECT * FROM snapshots;
```

### 4. Export for External ML Tools

```bash
# Export to Parquet for Spark/PyTorch
flink-sql -e "
  INSERT INTO filesystem_sink
  SELECT * FROM basket_analysis.basket_patterns;
"

# Or query directly with DuckDB
duckdb -c "
  SELECT * FROM parquet_scan('/tmp/paimon/basket_analysis.db/basket_patterns/**/*.parquet')
  WHERE confidence > 0.7;
"
```

## Maintenance

### Check Table Size

```bash
du -sh /tmp/paimon/basket_analysis.db/basket_patterns
```

### View Snapshots

```bash
ls -la /tmp/paimon/basket_analysis.db/basket_patterns/snapshot/
```

### Compact Table (Manual)

```sql
-- Trigger compaction
CALL sys.compact('basket_analysis.basket_patterns');
```

### Clean Old Snapshots

```sql
-- Remove snapshots older than 7 days
CALL sys.expire_snapshots('basket_analysis.basket_patterns', TIMESTAMP '2024-12-25 00:00:00');
```

## Troubleshooting

### Table Not Found

**Issue:** `Table 'basket_patterns' not found`

**Fix:**
```bash
# Re-run initialization
./init-paimon-training-data.sh

# Check catalog
ls -la /tmp/paimon/basket_analysis.db/
```

### No Data Ingested

**Issue:** Table exists but empty

**Check:**
```bash
# View logs from generator
./gradlew :flink-recommendations:run -PmainClass=HistoricalBasketDataGenerator

# Verify files exist
find /tmp/paimon/basket_analysis.db/basket_patterns -name "*.orc"
```

### Paimon Not Enabled in Live Job

**Issue:** Live job doesn't write to Paimon

**Fix:**
```bash
# Ensure PAIMON_WAREHOUSE is set
export PAIMON_WAREHOUSE=/tmp/paimon
./start-basket-job.sh

# Check logs for:
# "📦 Configuring Paimon sink for ML training data"
```

## Advanced Topics

### Incremental Updates

Paimon supports upserts with primary keys:

```java
// Patterns with same pattern_id will be updated, not duplicated
BasketPattern pattern = new BasketPattern(
    Arrays.asList("laptop"),
    "mouse",
    0.15,  // support
    0.80,  // confidence (updated from 0.75)
    2.3,   // lift (updated from 2.1)
    "electronics",
    "user_123",
    "session_456",
    System.currentTimeMillis()
);
```

### Change Data Capture

Enable CDC to track pattern changes:

```sql
-- Read changelog stream
SELECT * FROM basket_analysis.basket_patterns /*+ OPTIONS('changelog-producer'='input') */;
```

### Partitioning (Future Enhancement)

For very large datasets, partition by category:

```sql
CREATE TABLE basket_analysis.basket_patterns (
  ...
) PARTITIONED BY (category)
WITH (...);
```

## Implementation Details

### Hybrid Source (PaimonKafkaHybridSource.java)

The hybrid source uses **Table API** for clean, SQL-like data access:

```java
// Create Paimon catalog
String catalogDDL =
  "CREATE CATALOG IF NOT EXISTS paimon_catalog WITH (" +
  "  'type' = 'paimon'," +
  "  'warehouse' = '/tmp/paimon'" +
  ")";
tEnv.executeSql(catalogDDL);
tEnv.executeSql("USE CATALOG paimon_catalog");

// Read historical patterns
Table table = tEnv.sqlQuery(
  "SELECT antecedents, consequent, support, confidence, lift, category, user_id, session_id, created_at " +
  "FROM basket_analysis.basket_patterns"
);

// Convert to DataStream<BasketPattern>
DataStream<BasketPattern> paimonPatterns = tEnv.toDataStream(table)
  .map(row -> new BasketPattern(
    row.getField(0), // antecedents
    row.getField(1), // consequent
    row.getField(2), // support
    row.getField(3), // confidence
    row.getField(4), // lift
    row.getField(5), // category
    row.getField(6), // user_id
    row.getField(7), // session_id
    extractTimestamp(row.getField(8)) // created_at
  ));

// Create Kafka source for live patterns
KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
  .setBootstrapServers("localhost:19092")
  .setTopics("basket-patterns")
  .setStartingOffsets(OffsetsInitializer.latest())
  .build();

DataStream<BasketPattern> kafkaPatterns = env.fromSource(kafkaSource, ...)
  .process(new PatternParser());

// Union historical + live
DataStream<BasketPattern> allPatterns = paimonPatterns.union(kafkaPatterns);

// Broadcast to recommendation function
recommendations = events
  .keyBy(e -> e.sessionId)
  .connect(allPatterns.broadcast(PATTERNS_DESCRIPTOR))
  .process(new RecommendationGeneratorFunction());
```

**Key Benefits:**
- ✅ **Table API** provides clean, SQL-like interface
- ✅ **Automatic schema handling** - no manual field extraction
- ✅ **Graceful fallback** - uses sample patterns if table missing
- ✅ **Union operator** - seamlessly combines bounded + unbounded streams
- ✅ **Broadcast state** - all tasks get all patterns

### Sink Implementation (PaimonSinkHelper.java)

Writing to Paimon also uses Table API:

```java
// Convert DataStream to Table with computed columns
Table table = tEnv.fromDataStream(
  patterns,
  Schema.newBuilder()
    .column("antecedents", "ARRAY<STRING>")
    .column("consequent", "STRING>")
    .column("support", "DOUBLE")
    .column("confidence", "DOUBLE")
    .column("lift", "DOUBLE")
    .column("category", "STRING")
    .column("userId", "STRING")
    .column("sessionId", "STRING")
    .column("timestamp", "BIGINT")
    // Computed columns
    .columnByExpression("pattern_id",
      "CONCAT(ARRAY_JOIN(antecedents, ','), '_', consequent)")
    .columnByExpression("created_at",
      "TO_TIMESTAMP(FROM_UNIXTIME(timestamp / 1000))")
    .build()
);

// Insert into Paimon (automatic upsert based on primary key)
table.select(...)
  .executeInsert("basket_analysis.basket_patterns");
```

**Features:**
- ✅ **Upsert semantics** - primary key automatically deduplicates
- ✅ **Computed columns** - pattern_id and timestamps derived
- ✅ **Type safety** - schema enforced at compile time
- ✅ **Async execution** - non-blocking insert

## Next Steps

1. **Initialize Paimon:** `./init-paimon-training-data.sh`
2. **Start with pre-training:** `./start-basket-job-with-pretraining.sh` (recommended!)
3. **Query Data:** Use Flink SQL Client to explore patterns
4. **Train ML Model:** Read patterns from Paimon for training
5. **Monitor Growth:** Watch table size as live patterns accumulate
6. **Compare modes:** Try both cold start and warm start to see the difference

## Related Files

**Hybrid Source & Pre-training:**
- `PaimonKafkaHybridSource.java` - Hybrid source implementation (Table API)
- `BasketAnalysisJobWithPretraining.java` - Job with hybrid source (NEW)
- `start-basket-job-with-pretraining.sh` - Startup script for pre-training (NEW)

**Data Generation & Storage:**
- `HistoricalBasketDataGenerator.java` - Pattern generation logic
- `PaimonSinkHelper.java` - Paimon sink setup (Table API)
- `init-paimon-training-data.sh` - Initialization script

**Standard Mode (no pre-training):**
- `BasketAnalysisJobRefactored.java` - Standard job (writes to Paimon, doesn't read)
- `start-basket-job.sh` - Standard startup script
