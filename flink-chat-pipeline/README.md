# Flink Chat Pipeline - Advanced Streaming Patterns

A real-time chat message translation pipeline that demonstrates foundational Flink patterns and serves as a learning platform for advanced streaming concepts.

## Overview

This pipeline processes chat messages in real-time, enriching and translating them using LangChain4j. It's designed as an educational project demonstrating:

- **Current Implementation**: Basic stream processing with async I/O
- **Advanced Patterns (Lessons)**: CEP, broadcast state, lakehouse integration

```
Kafka: websocket_fanout
         │
         ▼
    ┌─────────────┐
    │   Parse     │
    │  Messages   │
    └──────┬──────┘
           │
           ▼
    ┌─────────────┐
    │   Enrich    │
    │  (add ID)   │
    └──────┬──────┘
           │
           ▼
    ┌─────────────┐
    │  Async I/O  │  ──────► OpenAI API
    │  Translate  │         (LangChain4j)
    └──────┬──────┘
           │
           ▼
Kafka: processing_fanout
```

## Quick Start

```bash
# Set API key
export OPENAI_API_KEY="sk-..."

# Build
./gradlew :flink-chat-pipeline:shadowJar

# Run
java -jar flink-chat-pipeline/build/libs/flink-chat-pipeline-*.jar \
  --kafka-brokers localhost:19092
```

## Current Architecture

### Input (Kafka: `websocket_fanout`)
```json
{
  "username": "alice",
  "text": "Hello world!",
  "type": "USER_JOINED",
  "preferredLanguage": "es",
  "timestamp": 1704067200
}
```

### Processing Steps

1. **ChatMessageEnricher** (`ChatMessageEnricher.java:19-21`)
   - Adds unique message ID
   - Simple stateless mapping

2. **LangChainAsyncFunction** (`LangChainAsyncFunction.java`)
   - Translates to preferred language
   - Uses OpenAI via LangChain4j
   - Async I/O with retry strategy

### Output (Kafka: `processing_fanout`)
```json
{
  "type": "chat_message",
  "timestamp": 1704067200,
  "payload": {
    "id": "msg-uuid",
    "username": "alice",
    "text": "Hello world!",
    "translated": "¡Hola mundo!",
    "language": "es"
  }
}
```

## Configuration

| Parameter | Default | Description |
|-----------|---------|-------------|
| `kafka-brokers` | `localhost:19092` | Kafka bootstrap servers |
| `api-key` | `$OPENAI_API_KEY` | OpenAI API key |
| `translation.modelName` | `gpt-4o-mini` | Translation model |

---

# 📚 Advanced Patterns - Lesson Plan

## Lesson 1: Complex Event Processing (CEP)

### What is CEP?

**Complex Event Processing** allows you to detect patterns in streams of events:
- Sequence patterns (A followed by B)
- Temporal constraints (within 5 minutes)
- Absence patterns (A NOT followed by B)
- Quantifiers (at least 3 occurrences)

### Use Cases for Chat Pipeline

1. **Spam Detection**
   ```
   Pattern: User sends 5+ messages within 10 seconds
   Action: Flag as potential spam
   ```

2. **Engagement Tracking**
   ```
   Pattern: User joins → asks question → receives answer → says thanks
   Action: Mark as successful interaction
   ```

3. **Abandonment Detection**
   ```
   Pattern: User joins → doesn't send message within 2 minutes
   Action: Trigger proactive greeting
   ```

4. **Conversation Completion**
   ```
   Pattern: Question → Answer → No follow-up for 30 seconds
   Action: Ask "Anything else?"
   ```

### Implementation Example

See: [CEP Patterns Documentation](./docs/CEP_PATTERNS.md)

```java
// Define pattern: Rapid message sending
Pattern<ChatMessage, ?> spamPattern = Pattern
    .<ChatMessage>begin("start")
    .where(msg -> msg.type == MessageType.USER_MESSAGE)
    .timesOrMore(5)  // 5 or more messages
    .within(Time.seconds(10));  // Within 10 seconds

// Apply pattern to stream
PatternStream<ChatMessage> patternStream = CEP.pattern(
    chatMessages.keyBy(msg -> msg.username),
    spamPattern
);

// Process matches
DataStream<Alert> alerts = patternStream.select(
    (Map<String, List<ChatMessage>> pattern) -> {
        List<ChatMessage> messages = pattern.get("start");
        return new SpamAlert(
            messages.get(0).username,
            messages.size(),
            "Rapid messaging detected"
        );
    }
);
```

### CEP Advantages
- ✅ Declarative pattern definition
- ✅ Built-in timeout handling
- ✅ State management for patterns
- ✅ Support for complex sequences

### When to Use CEP vs. ProcessFunction
- **Use CEP**: When you have clear event sequences or temporal patterns
- **Use ProcessFunction**: When you need custom state logic or arbitrary computations

---

## Lesson 2: Broadcast State

### What is Broadcast State?

**Broadcast state** allows you to share configuration or reference data across all parallel instances:
- Small configuration updates
- Rules that apply to all events
- Feature flags
- Block lists / allow lists

### Use Cases for Chat Pipeline

1. **Dynamic Moderation Rules**
   ```
   Broadcast: List of banned words
   All operators check messages against current rules
   Update rules without job restart
   ```

2. **User Permission Updates**
   ```
   Broadcast: User roles and permissions
   All instances enforce consistent access control
   Real-time permission revocation
   ```

3. **Translation Language Mappings**
   ```
   Broadcast: Language code → model mappings
   Update translation models dynamically
   A/B testing different models per language
   ```

4. **Feature Flags**
   ```
   Broadcast: Enabled features per tenant
   Toggle translation, profanity filter, etc.
   Gradual rollout capabilities
   ```

### Implementation Example

See: [Broadcast State Patterns Documentation](./docs/BROADCAST_STATE_PATTERNS.md)

```java
// Define state descriptor for broadcast rules
MapStateDescriptor<String, ModerationRule> ruleStateDescriptor =
    new MapStateDescriptor<>(
        "ModerationRules",
        BasicTypeInfo.STRING_TYPE_INFO,
        TypeInformation.of(ModerationRule.class)
    );

// Create broadcast stream
DataStream<ModerationRule> rulesStream = env
    .fromSource(kafkaRulesSource, ...)
    .broadcast(ruleStateDescriptor);

// Connect data stream with broadcast stream
BroadcastConnectedStream<ChatMessage, ModerationRule> connectedStream =
    chatMessages.connect(rulesStream);

// Process with broadcast state
DataStream<ModeratedMessage> moderated = connectedStream.process(
    new KeyedBroadcastProcessFunction<String, ChatMessage, ModerationRule, ModeratedMessage>() {

        @Override
        public void processElement(
            ChatMessage message,
            ReadOnlyContext ctx,
            Collector<ModeratedMessage> out) throws Exception {

            // Read current broadcast state
            ReadOnlyBroadcastState<String, ModerationRule> state =
                ctx.getBroadcastState(ruleStateDescriptor);

            // Apply rules
            for (Map.Entry<String, ModerationRule> entry : state.immutableEntries()) {
                ModerationRule rule = entry.getValue();
                if (rule.matches(message)) {
                    message = rule.apply(message);
                }
            }

            out.collect(new ModeratedMessage(message));
        }

        @Override
        public void processBroadcastElement(
            ModerationRule rule,
            Context ctx,
            Collector<ModeratedMessage> out) throws Exception {

            // Update broadcast state
            BroadcastState<String, ModerationRule> state =
                ctx.getBroadcastState(ruleStateDescriptor);
            state.put(rule.id, rule);
        }
    }
);
```

### Broadcast State Best Practices

1. **Keep Broadcast State Small**
   - Only configuration data, not large datasets
   - Typically < 1 MB
   - Stored in each task's memory

2. **Version Your Updates**
   ```java
   public class ModerationRule {
       String id;
       long version;  // Monotonically increasing
       // ... rule logic
   }
   ```

3. **Handle Missing State**
   ```java
   ModerationRule rule = state.get(ruleId);
   if (rule == null) {
       // Use default rule or skip
   }
   ```

4. **Test State Updates**
   - Send test updates to verify propagation
   - Monitor lag between broadcast and application

---

## Lesson 3: Historical Data from Lakes (Lakehouse Integration)

### What is Lakehouse Integration?

**Lakehouse** combines data lake (raw data) with warehouse (structure):
- Apache Iceberg / Delta Lake / Apache Hudi
- Time travel and versioning
- ACID transactions
- Schema evolution

### Use Cases for Chat Pipeline

1. **User History Enrichment**
   ```
   Stream: Current chat message
   Lake: Historical user behavior
   Output: Message enriched with user context
   ```

2. **Conversation Context Loading**
   ```
   Stream: New session start
   Lake: Previous conversations
   Output: Pre-loaded conversation history
   ```

3. **Sentiment Analysis Training Data**
   ```
   Stream: Real-time messages
   Lake: Historical sentiment labels
   Output: Model retraining on recent data
   ```

4. **Compliance and Audit**
   ```
   Stream: Current messages
   Lake: Historical violations
   Output: Pattern-based risk scoring
   ```

### Architecture Patterns

#### Pattern 1: Enrichment from Iceberg Table

See: [Lakehouse Integration Guide](./docs/LAKEHOUSE_INTEGRATION.md)

```java
// Load historical data from Iceberg
TableLoader tableLoader = TableLoader.fromHadoopTable(
    "s3://bucket/warehouse/user_profiles"
);

DataStream<UserProfile> historicalProfiles = FlinkSource
    .forRowData()
    .tableLoader(tableLoader)
    .streaming(false)  // Batch mode for historical
    .build();

// Convert to MapState for lookup
MapStateDescriptor<String, UserProfile> profileState =
    new MapStateDescriptor<>("profiles", String.class, UserProfile.class);

BroadcastStream<UserProfile> profilesBroadcast =
    historicalProfiles.broadcast(profileState);

// Enrich messages with historical data
DataStream<EnrichedMessage> enriched = chatMessages
    .connect(profilesBroadcast)
    .process(new HistoricalEnrichmentFunction(profileState));
```

#### Pattern 2: Real-Time Writes to Iceberg

```java
// Configure Iceberg sink
TableLoader tableLoader = TableLoader.fromCatalog(
    catalogLoader,
    TableIdentifier.of("default", "chat_messages")
);

FlinkSink.Builder<RowData> sinkBuilder = FlinkSink
    .forRowData(chatMessageStream)
    .tableLoader(tableLoader)
    .overwrite(false)
    .set("write.format.default", "parquet")
    .set("write.target-file-size-bytes", "134217728");  // 128 MB

// Write enriched messages to lake
enrichedMessages
    .map(msg -> convertToRowData(msg))
    .sinkTo(sinkBuilder.build());
```

#### Pattern 3: Temporal Joins with Iceberg

```java
// Create Flink table from Iceberg
TableEnvironment tableEnv = TableEnvironment.create(settings);

tableEnv.executeSql(
    "CREATE CATALOG iceberg_catalog WITH (\n" +
    "  'type'='iceberg',\n" +
    "  'catalog-type'='hadoop',\n" +
    "  'warehouse'='s3://bucket/warehouse'\n" +
    ")"
);

// Register Flink stream as table
tableEnv.createTemporaryView("chat_stream", chatMessages);

// Temporal join with historical data
Table result = tableEnv.sqlQuery(
    "SELECT \n" +
    "  c.username, \n" +
    "  c.text, \n" +
    "  u.total_messages, \n" +
    "  u.last_active\n" +
    "FROM chat_stream AS c\n" +
    "LEFT JOIN iceberg_catalog.default.user_profiles FOR SYSTEM_TIME AS OF c.event_time AS u\n" +
    "ON c.username = u.username"
);
```

### Lakehouse Integration Benefits

1. **Unified Storage**
   - Stream to lake in real-time
   - Query historical data for enrichment
   - Single source of truth

2. **Time Travel**
   ```java
   // Query data as of specific time
   TableLoader loader = TableLoader.fromHadoopTable(tablePath);
   loader.open();
   Table table = loader.loadTable();

   Snapshot snapshot = table.snapshot(snapshotId);
   // Read data from this version
   ```

3. **Schema Evolution**
   ```java
   // Add column without breaking readers
   table.updateSchema()
       .addColumn("sentiment_score", Types.FloatType.get())
       .commit();
   ```

4. **Incremental Processing**
   ```java
   // Process only new data since last checkpoint
   FlinkSource.forRowData()
       .tableLoader(tableLoader)
       .streaming(true)
       .startSnapshotId(lastProcessedSnapshot)
       .build()
   ```

### Lake vs. State Considerations

| Use Case | Use Flink State | Use Lakehouse |
|----------|----------------|---------------|
| Recent data (< 1 day) | ✅ Fast access | ❌ Slower |
| Historical data (> 1 day) | ❌ State too large | ✅ Efficient |
| Complex queries | ❌ Limited | ✅ SQL support |
| Point lookups | ✅ Very fast | ⚠️ Depends on partitioning |
| Compliance/audit | ❌ Not durable enough | ✅ WORM storage |
| Backfill/reprocessing | ❌ Requires restart | ✅ Easy |

---

## Lesson 4: Combining All Patterns

### Real-World Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Kafka: websocket_fanout                       │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
              ┌──────────────────────┐
              │  Parse Messages      │
              └──────────┬───────────┘
                         │
        ┌────────────────┴─────────────────┐
        │                                  │
        ▼                                  │
┌───────────────────┐                     │
│ Load User Context │ ◄─────┐             │
│ from Iceberg      │       │             │
│ (Historical Data) │       │             │
└────────┬──────────┘       │             │
         │                  │             │
         │            ┌─────────────┐     │
         │            │ Broadcast   │     │
         └───────────►│ Moderation  │◄────┤
                      │ Rules       │     │
                      └──────┬──────┘     │
                             │            │
                             ▼            │
                      ┌──────────────┐   │
                      │ Enrich +     │   │
                      │ Moderate     │◄──┘
                      └──────┬───────┘
                             │
                             ▼
                      ┌──────────────┐
                      │ CEP: Detect  │
                      │ Patterns     │
                      │ - Spam       │
                      │ - Engagement │
                      └──────┬───────┘
                             │
                             ▼
                      ┌──────────────┐
                      │ Async I/O    │ ──────► OpenAI API
                      │ Translate    │
                      └──────┬───────┘
                             │
                 ┌───────────┴───────────┐
                 │                       │
                 ▼                       ▼
         ┌──────────────┐      ┌──────────────┐
         │ Kafka Sink   │      │ Iceberg Sink │
         │ Real-time    │      │ Historical   │
         └──────────────┘      └──────────────┘
```

### Implementation Roadmap

**Phase 1: Foundation** (Current)
- [x] Basic stream processing
- [x] Async I/O with LangChain4j
- [x] Stateless enrichment

**Phase 2: CEP**
- [ ] Add spam detection pattern
- [ ] Engagement tracking
- [ ] Side output for alerts

**Phase 3: Broadcast State**
- [ ] Dynamic moderation rules
- [ ] Feature flags
- [ ] Configuration updates

**Phase 4: Lakehouse**
- [ ] Write to Iceberg
- [ ] Load user profiles
- [ ] Temporal joins

**Phase 5: Complete Integration**
- [ ] All patterns working together
- [ ] Performance optimization
- [ ] Production monitoring

---

## Exercises

### Exercise 1: Implement Spam Detection with CEP

**Goal**: Detect users sending too many messages

```java
// TODO: Implement spam detection
// 1. Define pattern: 5+ messages in 10 seconds
// 2. Apply to keyed stream by username
// 3. Generate SpamAlert for matches
// 4. Send to side output
```

**Solution**: See `docs/exercises/CEP_SPAM_DETECTION.md`

### Exercise 2: Add Broadcast State for Block List

**Goal**: Dynamically block certain words

```java
// TODO: Implement word blocking
// 1. Create broadcast stream from Kafka topic
// 2. Connect with message stream
// 3. Filter messages containing blocked words
// 4. Update block list without restart
```

**Solution**: See `docs/exercises/BROADCAST_BLOCK_LIST.md`

### Exercise 3: Enrich with Historical Data

**Goal**: Add user's message history count

```java
// TODO: Implement historical enrichment
// 1. Load user stats from Iceberg
// 2. Broadcast to all tasks
// 3. Enrich each message with total_messages count
// 4. Write enriched data back to Iceberg
```

**Solution**: See `docs/exercises/LAKEHOUSE_ENRICHMENT.md`

---

## Performance Considerations

### CEP Performance
- **Pattern Complexity**: Keep patterns simple, avoid deeply nested sequences
- **State Size**: Long timeouts = more state
- **Solution**: Use `afterMatchSkipStrategy` to limit buffering

### Broadcast State Performance
- **Size Matters**: Keep broadcast state < 1 MB
- **Update Frequency**: Don't broadcast constantly (batch updates)
- **Solution**: Aggregate updates before broadcasting

### Lakehouse Performance
- **Partitioning**: Partition by time for best query performance
- **File Size**: Target 128-512 MB files
- **Compaction**: Run regular compaction jobs
- **Solution**: Use Iceberg's built-in maintenance

---

## Additional Resources

### Documentation
- [CEP Patterns](./docs/CEP_PATTERNS.md) - Deep dive into complex event processing
- [Broadcast State Guide](./docs/BROADCAST_STATE_PATTERNS.md) - State management patterns
- [Lakehouse Integration](./docs/LAKEHOUSE_INTEGRATION.md) - Iceberg/Delta Lake setup
- [Performance Tuning](./docs/PERFORMANCE_TUNING.md) - Optimization strategies

### Examples
- [Exercise Solutions](./docs/exercises/) - Step-by-step implementations
- [Production Patterns](./docs/production/) - Battle-tested architectures

### External Resources
- [Flink CEP Documentation](https://nightlies.apache.org/flink/flink-docs-master/docs/libs/cep/)
- [Broadcast State Pattern](https://nightlies.apache.org/flink/flink-docs-master/docs/dev/datastream/fault-tolerance/broadcast_state/)
- [Flink Iceberg Integration](https://iceberg.apache.org/docs/latest/flink/)

---

## Next Steps

1. **Start Simple**: Run the current pipeline, understand the basics
2. **Add CEP**: Implement Exercise 1 (spam detection)
3. **Try Broadcast**: Implement Exercise 2 (block list)
4. **Integrate Lake**: Implement Exercise 3 (historical data)
5. **Go Production**: Combine all patterns, add monitoring

Ready to level up your Flink skills? Start with Lesson 1!
