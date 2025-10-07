# Lakehouse Integration with Apache Flink

## Introduction

A **Lakehouse** combines the benefits of data lakes (flexible, scalable storage) with data warehouses (ACID transactions, schema enforcement). This guide shows how to integrate Flink with lakehouse table formats for the chat pipeline.

## Supported Table Formats

| Format | Maturity | Flink Support | Best For |
|--------|----------|---------------|----------|
| **Apache Iceberg** | Production-ready | Excellent | Large-scale analytics |
| **Delta Lake** | Production-ready | Good | Databricks ecosystem |
| **Apache Hudi** | Production-ready | Good | CDC workloads |

This guide focuses on **Apache Iceberg** with Flink.

## Why Lakehouse for Chat Pipeline?

### Use Cases

1. **Historical Context Loading**
   ```
   User joins chat → Load their previous conversations from Iceberg
   → Provide personalized experience
   ```

2. **Long-Term Storage**
   ```
   Real-time messages → Stream to Kafka (hot path)
   → Also write to Iceberg (cold path)
   → Query historical data for analytics
   ```

3. **Compliance & Audit**
   ```
   All messages → Immutable storage in lake
   → Time travel to view historical state
   → Regulatory compliance
   ```

4. **ML Training Data**
   ```
   Labeled chat data → Stored in Iceberg
   → Incremental reads for model training
   → Efficient batch processing
   ```

## Architecture Patterns

### Pattern 1: Lambda Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Kafka Source                          │
└────────────────┬────────────────────────────────────────────┘
                 │
      ┌──────────┴──────────┐
      │                     │
      ▼                     ▼
┌──────────┐         ┌──────────────┐
│  Speed   │         │   Batch      │
│  Layer   │         │   Layer      │
│ (Flink)  │         │  (Iceberg)   │
│          │         │              │
│ Recent   │         │ Historical   │
│ State    │         │ Data         │
│ (Minutes)│         │ (Days+)      │
└────┬─────┘         └──────┬───────┘
     │                      │
     │  Enrich with         │
     │  historical data ◄───┘
     │
     ▼
┌──────────┐
│  Output  │
└──────────┘
```

### Pattern 2: Kappa Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Kafka Source                          │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ▼
          ┌─────────────┐
          │    Flink    │
          │  Processing │
          └──────┬──────┘
                 │
      ┌──────────┴──────────┐
      │                     │
      ▼                     ▼
┌──────────┐         ┌──────────────┐
│  Kafka   │         │   Iceberg    │
│ (Real-   │         │  (Long-term) │
│  time)   │         │              │
└──────────┘         └──────┬───────┘
                            │
                            │ Query for
                            │ enrichment
                            ▼
                     ┌──────────────┐
                     │  Flink SQL   │
                     │ Temporal Join│
                     └──────────────┘
```

## Setup: Apache Iceberg with Flink

### Dependencies

Add to `build.gradle`:

```gradle
dependencies {
    // Flink Iceberg connector
    implementation 'org.apache.iceberg:iceberg-flink-runtime-1.19:1.5.0'

    // AWS S3 (if using S3 as storage)
    implementation 'org.apache.iceberg:iceberg-aws:1.5.0'
    implementation 'software.amazon.awssdk:bundle:2.20.0'

    // Hadoop (for file system)
    implementation 'org.apache.hadoop:hadoop-common:3.3.4'
    implementation 'org.apache.hadoop:hadoop-aws:3.3.4'
}
```

### Configuration

```java
// Iceberg catalog configuration
Map<String, String> catalogProperties = new HashMap<>();
catalogProperties.put("type", "hadoop");
catalogProperties.put("warehouse", "s3://my-bucket/warehouse");
catalogProperties.put("io-impl", "org.apache.iceberg.aws.s3.S3FileIO");

// Or use Hive metastore
catalogProperties.put("type", "hive");
catalogProperties.put("uri", "thrift://metastore:9083");
catalogProperties.put("warehouse", "s3://my-bucket/warehouse");

// Or use AWS Glue
catalogProperties.put("type", "glue");
catalogProperties.put("warehouse", "s3://my-bucket/warehouse");
```

## Pattern 1: Writing Chat Messages to Iceberg

### Simple Sink

```java
import org.apache.iceberg.flink.*;
import org.apache.iceberg.*;
import org.apache.iceberg.catalog.*;

// 1. Create Iceberg table (one-time setup)
public void createTable(Catalog catalog) {
    TableIdentifier tableId = TableIdentifier.of("chat_db", "messages");

    Schema schema = new Schema(
        required(1, "message_id", Types.StringType.get()),
        required(2, "session_id", Types.StringType.get()),
        required(3, "username", Types.StringType.get()),
        required(4, "text", Types.StringType.get()),
        optional(5, "translated_text", Types.StringType.get()),
        optional(6, "language", Types.StringType.get()),
        required(7, "timestamp", Types.LongType.get()),
        optional(8, "sentiment_score", Types.DoubleType.get())
    );

    PartitionSpec spec = PartitionSpec.builderFor(schema)
        .day("timestamp")  // Partition by day
        .build();

    SortOrder sortOrder = SortOrder.builderFor(schema)
        .asc("timestamp")  // Sort by timestamp within partitions
        .build();

    catalog.createTable(
        tableId,
        schema,
        spec,
        sortOrder,
        ImmutableMap.of(
            "write.format.default", "parquet",
            "write.parquet.compression-codec", "zstd",
            "write.target-file-size-bytes", "134217728"  // 128 MB
        )
    );
}

// 2. Write to Iceberg from Flink
public DataStream<RowData> convertToRowData(DataStream<EnrichedChatMessage> messages) {
    return messages.map(msg -> {
        GenericRowData row = new GenericRowData(8);
        row.setField(0, StringData.fromString(msg.id));
        row.setField(1, StringData.fromString(msg.sessionId));
        row.setField(2, StringData.fromString(msg.username));
        row.setField(3, StringData.fromString(msg.text));
        row.setField(4, msg.translated != null ?
            StringData.fromString(msg.translated) : null);
        row.setField(5, msg.language != null ?
            StringData.fromString(msg.language) : null);
        row.setField(6, msg.timestamp);
        row.setField(7, msg.sentimentScore);
        return row;
    });
}

// 3. Configure Iceberg sink
TableLoader tableLoader = TableLoader.fromHadoopTable(
    "s3://bucket/warehouse/chat_db.db/messages"
);

FlinkSink.Builder<RowData> sinkBuilder = FlinkSink
    .forRowData(convertToRowData(enrichedMessages))
    .tableLoader(tableLoader)
    .overwrite(false)
    .set("write.format.default", "parquet")
    .set("write.target-file-size-bytes", "134217728");

enrichedMessages
    .map(this::convertToRowData)
    .sinkTo(sinkBuilder.build())
    .name("Iceberg Sink")
    .uid("iceberg-sink");
```

### Advanced: Upsert Mode

```java
// For tables with primary keys (deduplication)
FlinkSink.Builder<RowData> upsertSink = FlinkSink
    .forRowData(rowDataStream)
    .tableLoader(tableLoader)
    .overwrite(false)
    .equalityFieldColumns(Arrays.asList("message_id"))  // Primary key
    .set("write.upsert.enabled", "true");

// Iceberg will merge records with same message_id
// Newer records replace older ones
```

## Pattern 2: Reading Historical Data for Enrichment

### Batch Read

```java
// Read historical user profiles from Iceberg
TableLoader profileLoader = TableLoader.fromHadoopTable(
    "s3://bucket/warehouse/chat_db.db/user_profiles"
);

DataStream<UserProfile> historicalProfiles = FlinkSource
    .forRowData()
    .tableLoader(profileLoader)
    .streaming(false)  // Batch mode
    .build()
    .executeAndCollect()
    .stream()
    .map(this::convertToUserProfile);

// Build lookup map
Map<String, UserProfile> profileMap = new HashMap<>();
for (UserProfile profile : historicalProfiles) {
    profileMap.put(profile.userId, profile);
}

// Use in enrichment
DataStream<EnrichedMessage> enriched = chatMessages
    .map(new RichMapFunction<ChatMessage, EnrichedMessage>() {

        private Map<String, UserProfile> profiles;

        @Override
        public void open(Configuration parameters) {
            // Load profiles once when operator starts
            this.profiles = loadProfilesFromIceberg();
        }

        @Override
        public EnrichedMessage map(ChatMessage msg) {
            UserProfile profile = profiles.get(msg.userId);
            return EnrichedMessage.from(msg, profile);
        }
    });
```

### Streaming Read (Incremental)

```java
// Read new data as it arrives
TableLoader tableLoader = TableLoader.fromHadoopTable(tablePath);

DataStream<RowData> incrementalStream = FlinkSource
    .forRowData()
    .tableLoader(tableLoader)
    .streaming(true)  // Streaming mode
    .startSnapshotId(lastProcessedSnapshot)  // Start from checkpoint
    .monitorInterval(Duration.ofMinutes(1))  // Check for new data
    .build();

DataStream<ChatMessage> newMessages = env
    .fromSource(incrementalStream, WatermarkStrategy.noWatermarks(), "Iceberg Source")
    .map(this::convertToMessage);
```

## Pattern 3: Temporal Joins with Flink SQL

### Setup Table API

```java
StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

// 1. Create Iceberg catalog
tableEnv.executeSql(
    "CREATE CATALOG iceberg_catalog WITH (\n" +
    "  'type'='iceberg',\n" +
    "  'catalog-type'='hadoop',\n" +
    "  'warehouse'='s3://bucket/warehouse'\n" +
    ")"
);

tableEnv.executeSql("USE CATALOG iceberg_catalog");

// 2. Register streaming data as table
tableEnv.createTemporaryView("chat_stream",
    chatMessages,
    Schema.newBuilder()
        .column("message_id", DataTypes.STRING())
        .column("user_id", DataTypes.STRING())
        .column("text", DataTypes.STRING())
        .column("timestamp", DataTypes.BIGINT())
        .columnByExpression("event_time",
            "TO_TIMESTAMP(FROM_UNIXTIME(timestamp))")
        .watermark("event_time", "event_time - INTERVAL '5' SECOND")
        .build()
);

// 3. Temporal join with Iceberg table
String query =
    "SELECT \n" +
    "  c.message_id,\n" +
    "  c.user_id,\n" +
    "  c.text,\n" +
    "  u.total_messages,\n" +
    "  u.average_sentiment,\n" +
    "  u.preferred_language,\n" +
    "  u.last_active\n" +
    "FROM chat_stream AS c\n" +
    "LEFT JOIN chat_db.user_profiles FOR SYSTEM_TIME AS OF c.event_time AS u\n" +
    "ON c.user_id = u.user_id";

Table result = tableEnv.sqlQuery(query);

// Convert back to DataStream
DataStream<EnrichedMessage> enriched =
    tableEnv.toDataStream(result, EnrichedMessage.class);
```

### Lookup Join (Alternative)

```java
// For point lookups (simpler, but less efficient for large joins)
tableEnv.executeSql(
    "CREATE TABLE user_profiles (\n" +
    "  user_id STRING,\n" +
    "  total_messages BIGINT,\n" +
    "  preferred_language STRING,\n" +
    "  PRIMARY KEY (user_id) NOT ENFORCED\n" +
    ") WITH (\n" +
    "  'connector'='iceberg',\n" +
    "  'catalog-name'='iceberg_catalog',\n" +
    "  'catalog-type'='hadoop',\n" +
    "  'warehouse'='s3://bucket/warehouse',\n" +
    "  'database-name'='chat_db',\n" +
    "  'table-name'='user_profiles'\n" +
    ")"
);

// Lookup join (slower but simpler)
String lookupQuery =
    "SELECT c.*, u.total_messages, u.preferred_language\n" +
    "FROM chat_stream AS c\n" +
    "LEFT JOIN user_profiles FOR SYSTEM_TIME AS OF PROCTIME() AS u\n" +
    "ON c.user_id = u.user_id";
```

## Pattern 4: Broadcast Historical Data

For small historical datasets that fit in memory:

```java
// Load user preferences from Iceberg
TableLoader prefLoader = TableLoader.fromHadoopTable(
    "s3://bucket/warehouse/chat_db.db/user_preferences"
);

DataStream<UserPreference> preferences = FlinkSource
    .forRowData()
    .tableLoader(prefLoader)
    .streaming(false)
    .build()
    .map(this::convertToUserPreference);

// Broadcast to all tasks
MapStateDescriptor<String, UserPreference> prefDescriptor =
    new MapStateDescriptor<>("preferences",
        BasicTypeInfo.STRING_TYPE_INFO,
        TypeInformation.of(UserPreference.class));

BroadcastStream<UserPreference> prefsBroadcast =
    preferences.broadcast(prefDescriptor);

// Enrich messages with broadcast preferences
DataStream<EnrichedMessage> enriched = chatMessages
    .connect(prefsBroadcast)
    .process(new BroadcastProcessFunction<
        ChatMessage, UserPreference, EnrichedMessage>() {

        @Override
        public void processElement(
            ChatMessage msg,
            ReadOnlyContext ctx,
            Collector<EnrichedMessage> out) {

            ReadOnlyBroadcastState<String, UserPreference> prefs =
                ctx.getBroadcastState(prefDescriptor);

            UserPreference pref = prefs.get(msg.userId);
            out.collect(EnrichedMessage.from(msg, pref));
        }

        @Override
        public void processBroadcastElement(
            UserPreference pref,
            Context ctx,
            Collector<EnrichedMessage> out) {

            ctx.getBroadcastState(prefDescriptor)
                .put(pref.userId, pref);
        }
    });
```

## Advanced Features

### Time Travel

```java
// Query data as of specific snapshot
Table table = tableLoader.loadTable();
Snapshot snapshot = table.snapshot(snapshotId);

DataStream<RowData> historicalData = FlinkSource
    .forRowData()
    .tableLoader(tableLoader)
    .streaming(false)
    .startSnapshotId(snapshotId)  // Read from this snapshot
    .build();

// Query data as of specific timestamp
long timestampMillis = Instant.parse("2024-01-01T00:00:00Z").toEpochMilli();
Snapshot snapshotAtTime = table.snapshot(timestampMillis);
```

### Schema Evolution

```java
// Add column without breaking readers
Table table = tableLoader.loadTable();

table.updateSchema()
    .addColumn("sentiment_score", Types.DoubleType.get())
    .commit();

// Rename column
table.updateSchema()
    .renameColumn("text", "message_text")
    .commit();

// Delete column
table.updateSchema()
    .deleteColumn("old_field")
    .commit();

// Flink will handle schema evolution automatically
```

### Incremental Processing

```java
// Process only new data since last run
long lastProcessedSnapshot = getFromCheckpoint();

DataStream<RowData> incrementalData = FlinkSource
    .forRowData()
    .tableLoader(tableLoader)
    .streaming(true)
    .startSnapshotId(lastProcessedSnapshot)
    .build();

// Flink automatically tracks which data was processed
// On restart, continues from checkpoint
```

### Partitioned Reads

```java
// Only read specific partitions
DataStream<RowData> recentData = FlinkSource
    .forRowData()
    .tableLoader(tableLoader)
    .streaming(false)
    .filters(Arrays.asList(
        Expressions.greaterThanOrEqual("timestamp",
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7))
    ))
    .build();

// Or specific partition values
DataStream<RowData> todayData = FlinkSource
    .forRowData()
    .tableLoader(tableLoader)
    .streaming(false)
    .filters(Arrays.asList(
        Expressions.equal("day", "2024-01-01")
    ))
    .build();
```

## Maintenance Operations

### Compaction

```java
// Compact small files into larger ones
Table table = tableLoader.loadTable();

table.rewriteFiles()
    .option("target-file-size-bytes", "536870912")  // 512 MB
    .option("max-file-group-size-bytes", "107374182400")  // 100 GB
    .execute();

// Or use Flink action
String compactSQL =
    "CALL iceberg_catalog.system.rewrite_data_files(\n" +
    "  table => 'chat_db.messages',\n" +
    "  strategy => 'sort',\n" +
    "  sort_order => 'timestamp'\n" +
    ")";

tableEnv.executeSql(compactSQL);
```

### Expire Snapshots

```java
// Remove old snapshots (for cost optimization)
long retainLastNDays = 7;
long expireOlderThan = System.currentTimeMillis()
    - TimeUnit.DAYS.toMillis(retainLastNDays);

table.expireSnapshots()
    .expireOlderThan(expireOlderThan)
    .retainLast(10)  // Keep at least 10 snapshots
    .execute();

// Or use Flink action
String expireSQL =
    "CALL iceberg_catalog.system.expire_snapshots(\n" +
    "  table => 'chat_db.messages',\n" +
    "  older_than => TIMESTAMP '2024-01-01 00:00:00',\n" +
    "  retain_last => 10\n" +
    ")";
```

### Orphan File Cleanup

```java
// Remove orphaned data files
table.deleteOrphanFiles()
    .olderThan(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3))
    .execute();

// Or use Flink action
String cleanupSQL =
    "CALL iceberg_catalog.system.remove_orphan_files(\n" +
    "  table => 'chat_db.messages',\n" +
    "  older_than => TIMESTAMP '2024-01-01 00:00:00'\n" +
    ")";
```

## Performance Optimization

### 1. Partitioning Strategy

```java
// Time-based partitioning (most common)
PartitionSpec timePartition = PartitionSpec.builderFor(schema)
    .day("timestamp")  // Partition by day
    .build();

// Multi-dimensional partitioning
PartitionSpec multiPartition = PartitionSpec.builderFor(schema)
    .day("timestamp")
    .identity("tenant_id")  // Also partition by tenant
    .build();

// Avoid over-partitioning!
// BAD: .hour("timestamp") = Too many small partitions
// GOOD: .day("timestamp") or .month("timestamp")
```

### 2. File Sizing

```java
// Target 128-512 MB files
Map<String, String> properties = ImmutableMap.of(
    "write.target-file-size-bytes", "268435456",  // 256 MB
    "write.parquet.row-group-size-bytes", "134217728",  // 128 MB
    "write.parquet.page-size-bytes", "1048576"  // 1 MB
);
```

### 3. Compression

```java
// Use appropriate compression codec
properties.put("write.parquet.compression-codec", "zstd");

// Options:
// - zstd: Best compression, good speed (recommended)
// - snappy: Fast, moderate compression
// - gzip: Good compression, slower
// - uncompressed: Fastest, largest files
```

### 4. Sort Order

```java
// Sort data for better compression and filtering
SortOrder sortOrder = SortOrder.builderFor(schema)
    .asc("timestamp")
    .asc("user_id")
    .build();

// Iceberg uses sort statistics for query pruning
```

## Monitoring

### Metrics to Track

```java
// File count per partition
long fileCount = table.currentSnapshot().allManifests(table.io()).stream()
    .mapToLong(m -> m.existingFilesCount())
    .sum();

// Total data size
long totalSize = table.currentSnapshot().allManifests(table.io()).stream()
    .flatMap(m -> m.iterator())
    .mapToLong(f -> f.fileSizeInBytes())
    .sum();

// Snapshot count
int snapshotCount = 0;
for (Snapshot s : table.snapshots()) {
    snapshotCount++;
}

LOG.info("Table stats: files={} size={} snapshots={}",
    fileCount, totalSize, snapshotCount);
```

### Flink Metrics

```java
// Monitor Iceberg sink
FlinkSink.Builder<RowData> sink = FlinkSink
    .forRowData(rowDataStream)
    .tableLoader(tableLoader)
    // ... other config

// Metrics available in Flink UI:
// - numRecordsIn: Records received
// - numRecordsOut: Records written
// - currentSendTime: Write latency
// - numBytesOut: Bytes written
```

## Complete Example: Chat Pipeline with Iceberg

```java
public class ChatPipelineWithIceberg {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env =
            StreamExecutionEnvironment.getExecutionEnvironment();

        // 1. Read from Kafka
        DataStream<ChatMessage> messages = createKafkaSource(env);

        // 2. Load historical user data from Iceberg
        Map<String, UserHistory> userHistory = loadUserHistoryFromIceberg();

        // 3. Enrich messages
        DataStream<EnrichedMessage> enriched = messages
            .map(new HistoricalEnrichment(userHistory));

        // 4. Translate with LangChain4j
        DataStream<EnrichedMessage> translated =
            AsyncDataStream.orderedWait(
                enriched,
                new TranslationAsyncFunction(apiKey),
                60, TimeUnit.SECONDS, 100
            );

        // 5. Write to Kafka (hot path)
        translated.sinkTo(createKafkaSink());

        // 6. Write to Iceberg (cold path)
        TableLoader tableLoader = TableLoader.fromHadoopTable(
            "s3://bucket/warehouse/chat_db.db/messages"
        );

        translated
            .map(this::convertToRowData)
            .sinkTo(FlinkSink.forRowData()
                .tableLoader(tableLoader)
                .overwrite(false)
                .build())
            .name("Iceberg Cold Storage");

        env.execute("Chat Pipeline with Lakehouse");
    }
}
```

## Comparison: Lakehouse vs. Flink State

| Aspect | Flink State | Lakehouse (Iceberg) |
|--------|-------------|---------------------|
| **Latency** | Microseconds | Seconds |
| **Capacity** | Gigabytes | Petabytes |
| **Durability** | Checkpoints | WORM storage |
| **Queryability** | No SQL | Full SQL support |
| **Time Travel** | Limited | Yes (snapshots) |
| **Cost** | Memory/Disk | Object storage |
| **Use Case** | Recent data (<1 day) | Historical data (>1 day) |

## Best Practices

1. **Use Both**: Flink state for recent data, Iceberg for historical
2. **Partition Smart**: Day or hour partitions, not minute
3. **Compact Regular**: Schedule compaction jobs weekly
4. **Monitor Size**: Set alerts for large files or many partitions
5. **Test Queries**: Ensure partition pruning works
6. **Version Control**: Use snapshot IDs for reproducibility

## Next Steps

- Implement Exercise 3: Historical Enrichment
- Set up Iceberg catalog (Hive or Glue)
- Create your first table and write data
- Query historical data with Flink SQL
- Add maintenance jobs (compaction, expiration)
