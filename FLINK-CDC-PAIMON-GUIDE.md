# Flink CDC → Paimon Integration Guide

## Overview

This guide demonstrates how to use **Flink CDC (Change Data Capture)** to synchronize PostgreSQL tables into **Apache Paimon** tables in real-time, creating a unified data lake for both operational and analytical workloads.

## Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                         Data Flow                                │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  PostgreSQL (Operational DB)                                     │
│    ├─ products table                                            │
│    ├─ customers table                                           │
│    ├─ orders table                                              │
│    ├─ inventory table                                           │
│    │                                                             │
│    ▼ (WAL - Write Ahead Log)                                    │
│                                                                  │
│  Flink CDC Connector                                            │
│    ├─ Reads WAL via logical replication                        │
│    ├─ Converts to Flink Row format                             │
│    ├─ Handles INSERT, UPDATE, DELETE                           │
│    │                                                             │
│    ▼                                                             │
│                                                                  │
│  Paimon Tables (Data Lake)                                      │
│    ├─ lakehouse.products                                        │
│    ├─ lakehouse.customers                                       │
│    ├─ lakehouse.orders                                          │
│    ├─ lakehouse.inventory                                       │
│    │  - Automatic schema evolution                             │
│    │  - Primary key upserts                                    │
│    │  - Time-travel capabilities                               │
│    │                                                             │
│    ▼                                                             │
│                                                                  │
│  Flink Jobs (Analytics & Recommendations)                       │
│    ├─ Read products from Paimon                                │
│    ├─ Join with basket patterns                                │
│    ├─ Enrich recommendations                                   │
│    └─ Real-time dashboards                                     │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

## Why Flink CDC + Paimon?

### Traditional ETL Problems
- ❌ Batch ETL: Hours of latency
- ❌ Complex pipelines: Airflow/Spark jobs
- ❌ Schema drift: Manual DDL changes
- ❌ Data silos: Operational vs analytical DBs

### Flink CDC + Paimon Solution
- ✅ **Real-time**: Sub-second latency
- ✅ **No code**: Use `postgres_sync_table` action
- ✅ **Schema evolution**: Automatic DDL tracking
- ✅ **Unified lakehouse**: Single source of truth
- ✅ **Flink-native**: Table API reads

## Prerequisites

### Software Versions

| Component | Version | Notes |
|-----------|---------|-------|
| Flink | 1.18+ | Tested with 1.20.0 |
| Flink CDC | 3.3.0+ | Latest: 3.3.0 (Jan 2025) |
| Paimon | 0.9.0+ | Compatible with Flink 1.20 |
| PostgreSQL | 12+ | Tested with 15-alpine |

### Required JARs

Download these JARs and place in `flink/lib/`:

```bash
# Flink CDC Connector for PostgreSQL
wget https://repo1.maven.org/maven2/org/apache/flink/flink-cdc-pipeline-connector-postgres/3.3.0/flink-cdc-pipeline-connector-postgres-3.3.0.jar

# Paimon Flink Action JAR
wget https://repo1.maven.org/maven2/org/apache/paimon/paimon-flink-action/0.9.0/paimon-flink-action-0.9.0.jar

# Paimon Flink Bundle (includes all dependencies)
wget https://repo1.maven.org/maven2/org/apache/paimon/paimon-flink-1.20/0.9.0/paimon-flink-1.20-0.9.0.jar

# PostgreSQL JDBC Driver
wget https://jdbc.postgresql.org/download/postgresql-42.7.1.jar
```

## PostgreSQL CDC Setup

### 1. Start PostgreSQL with CDC Enabled

The `docker-compose.yml` already includes a PostgreSQL service with CDC configuration:

```bash
# Start PostgreSQL
docker compose up -d postgres

# Verify it's running
docker compose ps postgres

# Check logs
docker compose logs postgres
```

### 2. Verify CDC Configuration

```bash
# Connect to PostgreSQL
docker exec -it postgres-cdc psql -U postgres -d ecommerce

# Check WAL level (must be 'logical')
SHOW wal_level;
-- Expected: logical

# Check replication slots configuration
SHOW max_replication_slots;
-- Expected: 10

# List current publications (should include 'paimon_cdc')
\dRp+
-- Expected: paimon_cdc | t | t | t | t | t
```

### 3. Explore Sample Data

```sql
-- View products
SELECT product_id, product_name, category, price FROM products LIMIT 5;

-- View customers
SELECT customer_id, email, first_name, total_orders, lifetime_value FROM customers;

-- Check inventory
SELECT p.product_name, i.quantity_on_hand, i.quantity_available
FROM inventory i
JOIN products p ON i.product_id = p.product_id
LIMIT 10;
```

## Flink CDC → Paimon Synchronization

### Method 1: Single Table Sync (Recommended for Testing)

Synchronize a single PostgreSQL table to Paimon:

```bash
# Set environment variables
export PAIMON_WAREHOUSE=/tmp/paimon
export FLINK_HOME=/path/to/flink

# Run postgres_sync_table action
$FLINK_HOME/bin/flink run \
    $FLINK_HOME/lib/paimon-flink-action-0.9.0.jar \
    postgres_sync_table \
    --warehouse $PAIMON_WAREHOUSE \
    --database lakehouse \
    --table products \
    --postgres_conf hostname=localhost \
    --postgres_conf port=5432 \
    --postgres_conf username=postgres \
    --postgres_conf password=postgres \
    --postgres_conf database-name=ecommerce \
    --postgres_conf schema-name=public \
    --postgres_conf table-name=products \
    --postgres_conf slot.name=flink_products_slot \
    --catalog_conf metastore=filesystem \
    --catalog_conf warehouse=$PAIMON_WAREHOUSE
```

**What this does:**
1. Creates replication slot `flink_products_slot` in PostgreSQL
2. Reads current snapshot of `products` table
3. Creates Paimon table `lakehouse.products` with matching schema
4. Continuously streams INSERT/UPDATE/DELETE changes
5. Applies changes to Paimon with upsert semantics

### Method 2: Multiple Tables Sync

Synchronize multiple tables with a single command:

```bash
$FLINK_HOME/bin/flink run \
    $FLINK_HOME/lib/paimon-flink-action-0.9.0.jar \
    postgres_sync_table \
    --warehouse $PAIMON_WAREHOUSE \
    --database lakehouse \
    --postgres_conf hostname=localhost \
    --postgres_conf port=5432 \
    --postgres_conf username=postgres \
    --postgres_conf password=postgres \
    --postgres_conf database-name=ecommerce \
    --postgres_conf schema-name=public \
    --postgres_conf table-name='products|customers|orders|inventory' \
    --postgres_conf slot.name=flink_multi_slot \
    --catalog_conf metastore=filesystem \
    --catalog_conf warehouse=$PAIMON_WAREHOUSE
```

**Note:** Uses pipe `|` to separate multiple table names.

### Method 3: Regex Pattern Sync

Sync all tables matching a pattern:

```bash
$FLINK_HOME/bin/flink run \
    $FLINK_HOME/lib/paimon-flink-action-0.9.0.jar \
    postgres_sync_table \
    --warehouse $PAIMON_WAREHOUSE \
    --database lakehouse \
    --postgres_conf hostname=localhost \
    --postgres_conf port=5432 \
    --postgres_conf username=postgres \
    --postgres_conf password=postgres \
    --postgres_conf database-name=ecommerce \
    --postgres_conf schema-name=public \
    --postgres_conf table-name='prod.*' \  # Matches products, product_views, etc.
    --postgres_conf slot.name=flink_regex_slot \
    --catalog_conf metastore=filesystem \
    --catalog_conf warehouse=$PAIMON_WAREHOUSE
```

### Method 4: Entire Database Sync

Sync all tables in the database:

```bash
$FLINK_HOME/bin/flink run \
    $FLINK_HOME/lib/paimon-flink-action-0.9.0.jar \
    postgres_sync_database \
    --warehouse $PAIMON_WAREHOUSE \
    --database lakehouse \
    --postgres_conf hostname=localhost \
    --postgres_conf port=5432 \
    --postgres_conf username=postgres \
    --postgres_conf password=postgres \
    --postgres_conf database-name=ecommerce \
    --postgres_conf schema-name=public \
    --postgres_conf slot.name=flink_db_slot \
    --catalog_conf metastore=filesystem \
    --catalog_conf warehouse=$PAIMON_WAREHOUSE
```

## Configuration Options

### Essential PostgreSQL CDC Options

| Option | Description | Example | Required |
|--------|-------------|---------|----------|
| `hostname` | PostgreSQL host | `localhost` | Yes |
| `port` | PostgreSQL port | `5432` | Yes |
| `username` | DB username | `postgres` | Yes |
| `password` | DB password | `postgres` | Yes |
| `database-name` | Source database | `ecommerce` | Yes |
| `schema-name` | Schema name | `public` | Yes |
| `table-name` | Table(s) to sync | `products` or `prod.*` | Yes |
| `slot.name` | Replication slot | `flink_cdc_slot` | Yes |

### Optional PostgreSQL CDC Options

| Option | Description | Default | Notes |
|--------|-------------|---------|-------|
| `decoding.plugin.name` | Logical decoding plugin | `pgoutput` | Use `pgoutput` (default) or `wal2json` |
| `debezium.snapshot.mode` | Initial snapshot mode | `initial` | `initial`, `never`, `always` |
| `debezium.slot.drop.on.stop` | Drop slot on job stop | `false` | Set `true` for testing only |
| `scan.startup.mode` | Start mode | `initial` | `initial`, `latest-offset` |

### Paimon Catalog Options

| Option | Description | Example | Required |
|--------|-------------|---------|----------|
| `warehouse` | Paimon warehouse path | `/tmp/paimon` | Yes |
| `database` | Target database | `lakehouse` | Yes |
| `table` | Target table (single sync) | `products` | For single table |
| `metastore` | Catalog type | `filesystem` | Yes |

## Querying Paimon Tables

### Using Flink SQL Client

```bash
# Start Flink SQL Client
$FLINK_HOME/bin/sql-client.sh

-- Create Paimon catalog
CREATE CATALOG paimon_catalog WITH (
  'type' = 'paimon',
  'warehouse' = '/tmp/paimon'
);

USE CATALOG paimon_catalog;

-- List databases
SHOW DATABASES;

-- Use lakehouse database
USE lakehouse;

-- List tables
SHOW TABLES;

-- Query products (synced from PostgreSQL)
SELECT product_id, product_name, category, price
FROM products
ORDER BY price DESC
LIMIT 10;

-- Join with inventory
SELECT
    p.product_name,
    p.category,
    p.price,
    i.quantity_available,
    i.quantity_reserved
FROM products p
JOIN inventory i ON p.product_id = i.product_id
WHERE i.quantity_available < 20
ORDER BY i.quantity_available ASC;

-- Time-travel query (if you made changes)
SELECT COUNT(*) as product_count
FROM products
FOR SYSTEM_TIME AS OF TIMESTAMP '2025-01-20 10:00:00';
```

### Using Table API in Flink Job

```java
// Create Table Environment
StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
StreamTableEnvironment tEnv = StreamTableEnvironment.create(env);

// Register Paimon catalog
tEnv.executeSql(
    "CREATE CATALOG paimon_catalog WITH (" +
    "  'type' = 'paimon'," +
    "  'warehouse' = '/tmp/paimon'" +
    ")"
);

tEnv.executeSql("USE CATALOG paimon_catalog");
tEnv.executeSql("USE lakehouse");

// Query products table (synced from PostgreSQL via CDC)
Table products = tEnv.sqlQuery(
    "SELECT product_id, product_name, category, price " +
    "FROM products " +
    "WHERE category = 'electronics'"
);

// Convert to DataStream
DataStream<Row> productStream = tEnv.toDataStream(products);

// Process products (e.g., enrich recommendations)
productStream
    .map(row -> {
        String productId = row.getFieldAs("product_id");
        String name = row.getFieldAs("product_name");
        Double price = row.getFieldAs("price");
        // ... use in recommendation logic
        return new ProductInfo(productId, name, price);
    })
    .print();
```

## Testing CDC End-to-End

### 1. Start CDC Sync Job

```bash
# Start syncing products table
$FLINK_HOME/bin/flink run \
    $FLINK_HOME/lib/paimon-flink-action-0.9.0.jar \
    postgres_sync_table \
    --warehouse /tmp/paimon \
    --database lakehouse \
    --table products \
    --postgres_conf hostname=localhost \
    --postgres_conf port=5432 \
    --postgres_conf username=postgres \
    --postgres_conf password=postgres \
    --postgres_conf database-name=ecommerce \
    --postgres_conf schema-name=public \
    --postgres_conf table-name=products \
    --postgres_conf slot.name=test_slot
```

### 2. Verify Initial Snapshot

```bash
# Query Paimon table (should have all existing products)
$FLINK_HOME/bin/sql-client.sh <<EOF
CREATE CATALOG paimon_catalog WITH ('type' = 'paimon', 'warehouse' = '/tmp/paimon');
USE CATALOG paimon_catalog;
SELECT COUNT(*) as product_count FROM lakehouse.products;
EOF
```

**Expected:** ~44 products (from postgres-init.sql)

### 3. Test INSERT

```bash
# Insert new product in PostgreSQL
docker exec -it postgres-cdc psql -U postgres -d ecommerce -c "
INSERT INTO products (product_id, product_name, category, subcategory, brand, price, cost)
VALUES ('prod_test_001', 'Test Product', 'electronics', 'test', 'TestBrand', 99.99, 50.00);
"

# Wait 2-3 seconds for CDC to propagate

# Query Paimon (should include new product)
$FLINK_HOME/bin/sql-client.sh <<EOF
CREATE CATALOG paimon_catalog WITH ('type' = 'paimon', 'warehouse' = '/tmp/paimon');
USE CATALOG paimon_catalog;
SELECT product_id, product_name, price FROM lakehouse.products WHERE product_id = 'prod_test_001';
EOF
```

**Expected:** New product appears in Paimon within seconds

### 4. Test UPDATE

```bash
# Update price in PostgreSQL
docker exec -it postgres-cdc psql -U postgres -d ecommerce -c "
UPDATE products SET price = 79.99 WHERE product_id = 'prod_test_001';
"

# Query Paimon (price should be updated)
$FLINK_HOME/bin/sql-client.sh <<EOF
CREATE CATALOG paimon_catalog WITH ('type' = 'paimon', 'warehouse' = '/tmp/paimon');
USE CATALOG paimon_catalog;
SELECT product_id, price FROM lakehouse.products WHERE product_id = 'prod_test_001';
EOF
```

**Expected:** Price updated to 79.99

### 5. Test DELETE

```bash
# Delete product in PostgreSQL
docker exec -it postgres-cdc psql -U postgres -d ecommerce -c "
DELETE FROM products WHERE product_id = 'prod_test_001';
"

# Query Paimon (product should be deleted)
$FLINK_HOME/bin/sql-client.sh <<EOF
CREATE CATALOG paimon_catalog WITH ('type' = 'paimon', 'warehouse' = '/tmp/paimon');
USE CATALOG paimon_catalog;
SELECT COUNT(*) FROM lakehouse.products WHERE product_id = 'prod_test_001';
EOF
```

**Expected:** COUNT = 0 (product deleted)

## Integration with Recommendation Jobs

### Use Case: Enrich Recommendations with Product Details

```java
/**
 * Enhanced recommendation job that joins basket patterns with product catalog from Paimon
 */
public class EnrichedRecommendationJob {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        StreamTableEnvironment tEnv = StreamTableEnvironment.create(env);

        // Register Paimon catalog
        tEnv.executeSql(
            "CREATE CATALOG paimon_catalog WITH (" +
            "  'type' = 'paimon'," +
            "  'warehouse' = '/tmp/paimon'" +
            ")"
        );

        tEnv.executeSql("USE CATALOG paimon_catalog");

        // Read basket patterns (from pattern mining)
        Table patterns = tEnv.sqlQuery(
            "SELECT antecedents, consequent, confidence, support " +
            "FROM basket_analysis.basket_patterns " +
            "WHERE confidence > 0.5"
        );

        // Read product catalog (from PostgreSQL CDC)
        Table products = tEnv.sqlQuery(
            "SELECT product_id, product_name, category, price, image_url " +
            "FROM lakehouse.products " +
            "WHERE is_active = true"
        );

        // Join patterns with product details
        Table enrichedRecommendations = tEnv.sqlQuery(
            "SELECT " +
            "  p.consequent as product_id, " +
            "  pd.product_name, " +
            "  pd.category, " +
            "  pd.price, " +
            "  pd.image_url, " +
            "  p.confidence, " +
            "  p.support " +
            "FROM patterns p " +
            "JOIN products pd ON p.consequent = pd.product_id " +
            "ORDER BY p.confidence DESC"
        );

        // Convert to DataStream and output
        DataStream<Row> recommendations = tEnv.toDataStream(enrichedRecommendations);

        recommendations
            .map(row -> {
                String productId = row.getFieldAs("product_id");
                String name = row.getFieldAs("product_name");
                Double price = row.getFieldAs("price");
                Double confidence = row.getFieldAs("confidence");

                return new EnrichedRecommendation(productId, name, price, confidence);
            })
            .print();

        env.execute("Enriched Recommendation Job");
    }
}
```

### Use Case: Inventory-Aware Recommendations

```sql
-- Only recommend products that are in stock
SELECT
    bp.consequent as recommended_product,
    p.product_name,
    p.price,
    i.quantity_available,
    bp.confidence
FROM basket_analysis.basket_patterns bp
JOIN lakehouse.products p ON bp.consequent = p.product_id
JOIN lakehouse.inventory i ON p.product_id = i.product_id
WHERE
    bp.confidence > 0.6
    AND i.quantity_available > 0  -- In stock only
    AND p.is_active = true
ORDER BY bp.confidence DESC;
```

## Monitoring & Management

### Check Replication Slots

```bash
# List all replication slots
docker exec -it postgres-cdc psql -U postgres -d ecommerce -c "
SELECT
    slot_name,
    plugin,
    slot_type,
    active,
    restart_lsn,
    confirmed_flush_lsn
FROM pg_replication_slots;
"
```

**Expected output:**
```
    slot_name     | plugin  | slot_type | active | restart_lsn | confirmed_flush_lsn
------------------+---------+-----------+--------+-------------+---------------------
 flink_products_slot | pgoutput | logical   | t      | 0/1A2B3C4   | 0/1A2B3C4
```

### Monitor WAL Lag

```sql
-- Check WAL lag for each slot
SELECT
    slot_name,
    pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn)) as lag_size,
    active
FROM pg_replication_slots;
```

### Clean Up Unused Slots

```bash
# If a Flink job crashes, manually drop the slot
docker exec -it postgres-cdc psql -U postgres -d ecommerce -c "
SELECT pg_drop_replication_slot('flink_products_slot');
"
```

**Warning:** Only drop slots if you're sure the Flink job is not running!

### Check Paimon Table Metadata

```bash
# List snapshots
$FLINK_HOME/bin/sql-client.sh <<EOF
CREATE CATALOG paimon_catalog WITH ('type' = 'paimon', 'warehouse' = '/tmp/paimon');
USE CATALOG paimon_catalog;
SELECT * FROM lakehouse.products\$snapshots;
EOF

# Check table options
$FLINK_HOME/bin/sql-client.sh <<EOF
CREATE CATALOG paimon_catalog WITH ('type' = 'paimon', 'warehouse' = '/tmp/paimon');
USE CATALOG paimon_catalog;
SHOW CREATE TABLE lakehouse.products;
EOF
```

## Troubleshooting

### Issue: "ERROR: replication slot already exists"

**Cause:** Slot from previous job still active

**Fix:**
```bash
# Drop the slot
docker exec -it postgres-cdc psql -U postgres -d ecommerce -c "
SELECT pg_drop_replication_slot('your_slot_name');
"

# Or use a different slot name
--postgres_conf slot.name=flink_products_slot_v2
```

### Issue: "ERROR: must be superuser or replication role"

**Cause:** User lacks replication permissions

**Fix:**
```bash
# Grant replication permission
docker exec -it postgres-cdc psql -U postgres -d ecommerce -c "
ALTER USER postgres WITH REPLICATION;
"
```

### Issue: "WAL level must be 'logical'"

**Cause:** PostgreSQL not configured for CDC

**Fix:** Already configured in docker-compose.yml, but verify:
```bash
docker exec -it postgres-cdc psql -U postgres -d ecommerce -c "SHOW wal_level;"
```

Should return: `logical`

### Issue: Paimon table not updating

**Cause:** Flink CDC job not running or crashed

**Check:**
```bash
# List running Flink jobs
$FLINK_HOME/bin/flink list

# Check Flink logs
tail -f $FLINK_HOME/log/flink-*-taskexecutor-*.log
```

### Issue: High WAL disk usage

**Cause:** Replication slot lag (consumer not keeping up)

**Fix:**
```bash
# Check lag
docker exec -it postgres-cdc psql -U postgres -d ecommerce -c "
SELECT
    slot_name,
    pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn)) as lag
FROM pg_replication_slots;
"

# If lag is large, increase Flink parallelism or drop slow slot
```

## Performance Tuning

### PostgreSQL Side

```sql
-- Increase WAL buffer for high throughput
ALTER SYSTEM SET wal_buffers = '16MB';

-- Tune checkpoint frequency
ALTER SYSTEM SET checkpoint_timeout = '10min';
ALTER SYSTEM SET max_wal_size = '2GB';

-- Reload config
SELECT pg_reload_conf();
```

### Flink Side

```bash
# Increase parallelism for faster processing
--parallelism 4

# Tune checkpoint interval
--checkpointing-interval 30000  # 30 seconds

# Increase network buffers
--network-buffer-count 4096
```

### Paimon Side

Configure in Flink SQL or via catalog options:

```sql
-- Faster compaction
ALTER TABLE lakehouse.products SET (
    'compaction.min.file-num' = '3',
    'compaction.max.file-num' = '20',
    'snapshot.num-retained.min' = '5'
);
```

## Best Practices

### 1. Slot Naming Convention

Use descriptive slot names:
```
flink_{table}_{env}_slot
```

Examples:
- `flink_products_prod_slot`
- `flink_orders_dev_slot`

### 2. Checkpoint Configuration

```bash
# Enable checkpointing every 30 seconds
--checkpointing-interval 30000

# Use RocksDB for large state
--state-backend rocksdb
```

### 3. Schema Evolution

Paimon handles schema changes automatically:
- New columns: Added to Paimon table
- Dropped columns: Marked as NULL in Paimon
- Type changes: May require manual handling

### 4. Resource Management

Monitor Flink task managers:
```bash
# Check Flink Web UI
http://localhost:8081

# Monitor task manager logs
tail -f $FLINK_HOME/log/flink-*-taskexecutor-*.log
```

### 5. Backup & Recovery

```bash
# Backup Paimon warehouse
tar -czf paimon-backup-$(date +%Y%m%d).tar.gz /tmp/paimon

# Backup PostgreSQL replication slots
docker exec postgres-cdc pg_dump -U postgres ecommerce > ecommerce-backup.sql
```

## Advanced Topics

### Schema Evolution Example

```bash
# Add new column to PostgreSQL
docker exec -it postgres-cdc psql -U postgres -d ecommerce -c "
ALTER TABLE products ADD COLUMN sku VARCHAR(50);
UPDATE products SET sku = 'SKU-' || product_id WHERE sku IS NULL;
"

# Paimon automatically adds the column (check after a few seconds)
$FLINK_HOME/bin/sql-client.sh <<EOF
CREATE CATALOG paimon_catalog WITH ('type' = 'paimon', 'warehouse' = '/tmp/paimon');
USE CATALOG paimon_catalog;
DESCRIBE lakehouse.products;
EOF
```

### Computed Columns in Paimon

```bash
# Sync with computed columns
$FLINK_HOME/bin/flink run \
    $FLINK_HOME/lib/paimon-flink-action-0.9.0.jar \
    postgres_sync_table \
    --warehouse /tmp/paimon \
    --database lakehouse \
    --table products \
    --computed_column 'price_category=CASE WHEN price < 50 THEN ''budget'' WHEN price < 200 THEN ''mid'' ELSE ''premium'' END' \
    --postgres_conf hostname=localhost \
    --postgres_conf port=5432 \
    --postgres_conf username=postgres \
    --postgres_conf password=postgres \
    --postgres_conf database-name=ecommerce \
    --postgres_conf schema-name=public \
    --postgres_conf table-name=products \
    --postgres_conf slot.name=flink_computed_slot
```

### Metadata Columns

Add CDC metadata to Paimon table:

```bash
--metadata_column '_op_type'  # INSERT, UPDATE, DELETE
--metadata_column '_event_timestamp'  # When change occurred
```

## Next Steps

1. **Start PostgreSQL:** `docker compose up -d postgres`
2. **Verify setup:** `docker compose logs postgres`
3. **Download JARs:** See "Required JARs" section
4. **Run first sync:** Use Method 1 (Single Table Sync)
5. **Test CDC:** Follow "Testing CDC End-to-End"
6. **Integrate with jobs:** See "Integration with Recommendation Jobs"
7. **Monitor:** Set up slot monitoring queries

## Related Documentation

- [Apache Paimon PostgreSQL CDC](https://paimon.apache.org/docs/0.9/flink/cdc-ingestion/postgres-cdc/)
- [Flink CDC 3.3.0 Release](https://flink.apache.org/2025/01/21/apache-flink-cdc-3.3.0-release-announcement/)
- [PostgreSQL Logical Replication](https://www.postgresql.org/docs/current/logical-replication.html)
- [PAIMON-TRAINING-DATA.md](./PAIMON-TRAINING-DATA.md) - Pattern mining with Paimon
- [BASKET-RECOMMENDATIONS-DEMO.md](./BASKET-RECOMMENDATIONS-DEMO.md) - Recommendation system demo

## Summary

You now have:
- ✅ PostgreSQL with CDC enabled
- ✅ Sample e-commerce schema
- ✅ Complete Flink CDC → Paimon guide
- ✅ Real-time data lake pipeline
- ✅ Integration with Flink jobs

**Result:** Operational data (PostgreSQL) → Real-time lakehouse (Paimon) → Analytics & ML (Flink)
