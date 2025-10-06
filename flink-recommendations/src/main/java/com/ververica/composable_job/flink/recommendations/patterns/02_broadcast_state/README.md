# 📡 FLINK PATTERN: Broadcast State

> **Learning Pattern 02**: Distribute low-throughput data (ML models, configs, rules) to ALL parallel tasks

---

## 📚 Learning Objectives

After completing this module, you will be able to:

1. ✅ **Understand broadcast state** - Replicate state across all parallel tasks
2. ✅ **Connect two streams** - High-throughput main + low-throughput broadcast
3. ✅ **Distribute ML models** - Update models dynamically during job execution
4. ✅ **Enrich data efficiently** - Avoid joins for dimension table lookups
5. ✅ **Handle state updates** - Read-only vs read-write broadcast state access

---

## 🎯 What is Broadcast State?

**Broadcast state** replicates low-throughput data to **ALL parallel tasks**, enabling efficient enrichment of high-throughput streams.

### Key Characteristics

| Feature | Description |
|---------|-------------|
| **Replicated** | Same state available on ALL parallel tasks |
| **Two Streams** | Main (high-throughput) + Broadcast (low-throughput) |
| **Read-Only in Main** | Can only read broadcast state when processing main stream |
| **Read-Write in Broadcast** | Can update broadcast state when processing broadcast stream |
| **No Shuffle** | Broadcast stream sent to all tasks without partitioning |

### Visual Representation

```
TRADITIONAL KEYED STATE (different value per task):
┌────────────────────────────────────────────────────┐
│ Main Stream: User Events (keyed by user ID)       │
└────────────┬───────────────┬────────────┬──────────┘
             │               │            │
    ┌────────▼──────┐ ┌─────▼──────┐ ┌──▼──────────┐
    │ Task 1        │ │ Task 2     │ │ Task 3      │
    │ Users: A,B,C  │ │ Users: D,E │ │ Users: F,G  │  ← Different users
    └───────────────┘ └────────────┘ └─────────────┘


BROADCAST STATE (same value on all tasks):
┌────────────────────────────────────────────────────┐
│ Broadcast Stream: ML Model Updates (1/hour)       │
└────────────┬───────────────┬────────────┬──────────┘
             │               │            │
    ┌────────▼──────┐ ┌─────▼──────┐ ┌──▼──────────┐
    │ Task 1        │ │ Task 2     │ │ Task 3      │
    │ Model: v2     │ │ Model: v2  │ │ Model: v2   │  ← Same model!
    └───────▲───────┘ └──────▲─────┘ └──────▲──────┘
            │                │               │
    ┌───────┴────────────────┴───────────────┴──────┐
    │ Main Stream: User Events (1000s/sec)          │
    │ Each task uses its local copy of the model    │
    └───────────────────────────────────────────────┘
```

---

## 🔄 Broadcast State vs Other Patterns

### Comparison Table

| Pattern | State Scope | Use Case | Update Frequency |
|---------|-------------|----------|------------------|
| **Keyed State** | Per key (different per partition) | User profiles, inventory | Every event |
| **Broadcast State** | Global (same across all tasks) | ML models, configs, rules | Hourly/daily |
| **Side Inputs** | Static (loaded at startup) | Reference data | Never (restart required) |
| **Join** | N/A (shuffles data) | Large-to-large joins | Continuous |

### When to Use Broadcast State

✅ **Use broadcast state when:**
- Low-throughput updates (1-100/hour)
- All tasks need same data (ML model, config)
- Updates are dynamic (during job execution)
- Main stream is high-throughput (1000s/sec)

❌ **Don't use broadcast state when:**
- Broadcast data is huge (>100 MB) - use distributed cache or database
- Updates are frequent (>1000/sec) - use keyed state instead
- Data is per-key specific - use keyed state
- Data is static - use side inputs

---

## 🤖 Real-World Example: ML Model Distribution

### Scenario

An e-commerce site uses ML to recommend products:
- **Main stream:** User browsing events (1000s/sec)
- **Broadcast stream:** Updated ML model (1/hour)
- **Goal:** Every task has latest model for fast inference

### Without Broadcast State ❌

```
User event arrives
  ↓
Fetch model from database (network call!)
  ↓
Apply model
  ↓
Return recommendation

Problem: 1000 events/sec × network latency = SLOW!
```

### With Broadcast State ✅

```
ML model update arrives (1/hour)
  ↓
Broadcast to all tasks
  ↓
All tasks cache model locally

User event arrives (1000s/sec)
  ↓
Apply local model (no network!)
  ↓
Return recommendation

Benefit: Local model = FAST (microseconds vs milliseconds)
```

---

## 💻 Code Walkthrough

### Step 1: Create Two Streams

```java
// HIGH-THROUGHPUT: User events (1000s per second)
DataStream<UserEvent> userEvents = env
    .addSource(new KafkaSource<>("user-events"))
    .name("User Events");

// LOW-THROUGHPUT: ML model updates (1 per hour)
DataStream<MLModel> modelUpdates = env
    .addSource(new KafkaSource<>("ml-model-updates"))
    .name("ML Model Updates");
```

---

### Step 2: Define Broadcast State Descriptor

```java
// This descriptor defines the structure of broadcast state
MapStateDescriptor<String, MLModel> modelStateDescriptor = new MapStateDescriptor<>(
    "ml-model-state",      // State name (for checkpoints)
    Types.STRING,          // Key type (we use "current-model" as key)
    Types.POJO(MLModel.class)  // Value type (the model)
);
```

**Why MapStateDescriptor?**
- Allows multiple models (e.g., "product-model", "user-model")
- Key-value structure: `"current-model" → MLModel(v2)`

---

### Step 3: Create Broadcast Stream

```java
// Convert model stream to broadcast stream
// This will replicate every model update to ALL parallel tasks
BroadcastStream<MLModel> broadcastStream = modelUpdates
    .broadcast(modelStateDescriptor);
```

**What `.broadcast()` does:**
- Tags the stream as "broadcast"
- Flink sends each element to **every** parallel task
- No partitioning/shuffling - pure replication

---

### Step 4: Connect Streams

```java
DataStream<Recommendation> recommendations = userEvents
    .connect(broadcastStream)  // Connect main + broadcast
    .process(new RecommendationEnricher(modelStateDescriptor))
    .name("Apply ML Model");
```

**Key points:**
- `connect()` creates a `BroadcastConnectedStream`
- Both streams can now be processed together
- Each task processes both its partition of userEvents + all model updates

---

### Step 5: Process Main Stream (Read-Only)

```java
public class RecommendationEnricher
        extends BroadcastProcessFunction<UserEvent, MLModel, Recommendation> {

    @Override
    public void processElement(
            UserEvent event,
            ReadOnlyContext ctx,  // ← Note: ReadOnlyContext!
            Collector<Recommendation> out) throws Exception {

        // GET broadcast state (READ-ONLY)
        ReadOnlyBroadcastState<String, MLModel> broadcastState =
            ctx.getBroadcastState(modelStateDescriptor);

        MLModel currentModel = broadcastState.get("current-model");

        if (currentModel == null) {
            LOG.warn("No model loaded yet, skipping event");
            return;
        }

        // Apply model to generate recommendations
        List<String> recommendedProducts = currentModel.predict(event);

        Recommendation recommendation = new Recommendation();
        recommendation.userId = event.userId;
        recommendation.recommendedProducts = recommendedProducts;
        recommendation.modelVersion = currentModel.version;

        out.collect(recommendation);
    }
}
```

**Important:**
- `ReadOnlyContext` - cannot modify broadcast state here
- `broadcastState.get()` - read current model
- Fast local access - no network calls!

---

### Step 6: Process Broadcast Stream (Read-Write)

```java
@Override
public void processBroadcastElement(
        MLModel newModel,
        Context ctx,  // ← Full Context (read-write access)
        Collector<Recommendation> out) throws Exception {

    // UPDATE broadcast state (READ-WRITE)
    BroadcastState<String, MLModel> broadcastState =
        ctx.getBroadcastState(modelStateDescriptor);

    // Replace old model with new model
    broadcastState.put("current-model", newModel);

    LOG.info("🔄 ML Model updated to v{} (accuracy: {}%)",
        newModel.version, newModel.accuracy * 100);

    LOG.info("✅ Model v{} now active across ALL {} parallel tasks",
        newModel.version,
        getRuntimeContext().getNumberOfParallelSubtasks());
}
```

**Key differences:**
- `Context` (not `ReadOnlyContext`) - full read-write access
- `broadcastState.put()` - update the model
- Called on **every** parallel task (broadcast replication!)

---

## 🎓 Hands-On Exercises

### Exercise 1: Add Model Metrics

**Task:** Track how many predictions each model version made.

**Steps:**
1. Add a counter state to track predictions per model version
2. Increment counter in `processElement()`
3. Log metrics in `processBroadcastElement()` before model update

**Solution:**

```java
public class RecommendationEnricher
        extends BroadcastProcessFunction<UserEvent, MLModel, Recommendation> {

    // Add keyed state for metrics (per parallel task)
    private transient ValueState<Long> predictionCountState;

    @Override
    public void open(Configuration parameters) {
        predictionCountState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("prediction-count", Long.class)
        );
    }

    @Override
    public void processElement(UserEvent event, ReadOnlyContext ctx, Collector<Recommendation> out)
            throws Exception {

        ReadOnlyBroadcastState<String, MLModel> broadcastState =
            ctx.getBroadcastState(modelStateDescriptor);

        MLModel currentModel = broadcastState.get("current-model");
        if (currentModel == null) return;

        // Increment prediction counter
        Long count = predictionCountState.value();
        predictionCountState.update(count == null ? 1 : count + 1);

        // Apply model
        List<String> recommendations = currentModel.predict(event);

        Recommendation rec = new Recommendation();
        rec.userId = event.userId;
        rec.recommendedProducts = recommendations;
        rec.modelVersion = currentModel.version;

        out.collect(rec);
    }

    @Override
    public void processBroadcastElement(MLModel newModel, Context ctx, Collector<Recommendation> out)
            throws Exception {

        // Log metrics BEFORE updating model
        Long predictionsMade = predictionCountState.value();
        ReadOnlyBroadcastState<String, MLModel> broadcastState =
            ctx.getBroadcastState(modelStateDescriptor);
        MLModel oldModel = broadcastState.get("current-model");

        if (oldModel != null) {
            LOG.info("📊 Model v{} made {} predictions on task {}",
                oldModel.version,
                predictionsMade == null ? 0 : predictionsMade,
                getRuntimeContext().getIndexOfThisSubtask());
        }

        // Update model
        ctx.getBroadcastState(modelStateDescriptor).put("current-model", newModel);

        // Reset counter for new model
        predictionCountState.update(0L);

        LOG.info("✅ Model updated to v{}", newModel.version);
    }
}
```

**Expected Output:**
```
📊 Model v1 made 1523 predictions on task 0
📊 Model v1 made 1487 predictions on task 1
📊 Model v1 made 1511 predictions on task 2
✅ Model updated to v2
```

---

### Exercise 2: Multi-Model Broadcast State

**Task:** Support multiple models (product recommendations + user preferences).

**Requirements:**
1. Add second broadcast stream for user preference model
2. Use different keys in MapState: "product-model", "user-model"
3. Combine predictions from both models

**Solution:**

```java
// Step 1: Define state descriptor (same one, but we'll use multiple keys)
MapStateDescriptor<String, MLModel> modelStateDescriptor = new MapStateDescriptor<>(
    "ml-models",  // Changed name to plural
    Types.STRING,
    Types.POJO(MLModel.class)
);

// Step 2: Create two broadcast streams
BroadcastStream<MLModel> productModelBroadcast = productModelUpdates
    .broadcast(modelStateDescriptor);

BroadcastStream<MLModel> userModelBroadcast = userModelUpdates
    .broadcast(modelStateDescriptor);

// Step 3: Connect both (need to do this sequentially)
DataStream<Recommendation> recommendations = userEvents
    .connect(productModelBroadcast)
    .process(new MultiModelEnricher(modelStateDescriptor, "product-model"))
    .connect(userModelBroadcast)
    .process(new MultiModelEnricher(modelStateDescriptor, "user-model"));

// Step 4: Modified processor
public class MultiModelEnricher
        extends BroadcastProcessFunction<UserEvent, MLModel, Recommendation> {

    private final String modelKey;  // "product-model" or "user-model"

    public MultiModelEnricher(MapStateDescriptor<String, MLModel> descriptor, String modelKey) {
        this.modelStateDescriptor = descriptor;
        this.modelKey = modelKey;
    }

    @Override
    public void processElement(UserEvent event, ReadOnlyContext ctx, Collector<Recommendation> out)
            throws Exception {

        ReadOnlyBroadcastState<String, MLModel> state = ctx.getBroadcastState(modelStateDescriptor);

        // Get BOTH models
        MLModel productModel = state.get("product-model");
        MLModel userModel = state.get("user-model");

        if (productModel == null || userModel == null) {
            LOG.warn("Waiting for both models to load...");
            return;
        }

        // Combine predictions from both models
        List<String> productRecs = productModel.predict(event);
        List<String> userRecs = userModel.predict(event);

        // Merge and deduplicate
        Set<String> combined = new HashSet<>();
        combined.addAll(productRecs.subList(0, Math.min(3, productRecs.size())));
        combined.addAll(userRecs.subList(0, Math.min(2, userRecs.size())));

        Recommendation rec = new Recommendation();
        rec.userId = event.userId;
        rec.recommendedProducts = new ArrayList<>(combined);
        rec.modelVersion = productModel.version; // or combine both

        out.collect(rec);
    }

    @Override
    public void processBroadcastElement(MLModel newModel, Context ctx, Collector<Recommendation> out)
            throws Exception {

        BroadcastState<String, MLModel> state = ctx.getBroadcastState(modelStateDescriptor);

        // Update the specific model (product or user)
        state.put(modelKey, newModel);

        LOG.info("✅ {} updated to v{}", modelKey, newModel.version);
    }
}
```

**Use Case:**
- Product model: "Users who viewed X also viewed Y"
- User model: "Based on your history, you might like Z"
- Combined: Best of both approaches!

---

### Exercise 3: Feature Flags with Broadcast State

**Task:** Use broadcast state to distribute feature flags for A/B testing.

**Requirements:**
1. Create `FeatureFlag` class with flag name + enabled status
2. Broadcast feature flags to all tasks
3. Apply different logic based on flag status

**Solution:**

```java
// Feature flag class
public class FeatureFlag implements Serializable {
    public String flagName;
    public boolean enabled;
    public Map<String, String> config;  // Additional parameters

    public FeatureFlag(String flagName, boolean enabled) {
        this.flagName = flagName;
        this.enabled = enabled;
        this.config = new HashMap<>();
    }
}

// State descriptor
MapStateDescriptor<String, FeatureFlag> flagStateDescriptor = new MapStateDescriptor<>(
    "feature-flags",
    Types.STRING,
    Types.POJO(FeatureFlag.class)
);

// Broadcast feature flags
BroadcastStream<FeatureFlag> flagBroadcast = featureFlagStream
    .broadcast(flagStateDescriptor);

// Processor
public class FeatureAwareProcessor
        extends BroadcastProcessFunction<UserEvent, FeatureFlag, Recommendation> {

    @Override
    public void processElement(UserEvent event, ReadOnlyContext ctx, Collector<Recommendation> out)
            throws Exception {

        ReadOnlyBroadcastState<String, FeatureFlag> flags = ctx.getBroadcastState(flagStateDescriptor);

        // Check feature flags
        FeatureFlag newAlgoFlag = flags.get("use-new-recommendation-algorithm");
        FeatureFlag personalizeFlag = flags.get("enable-personalization");

        List<String> recommendations;

        if (newAlgoFlag != null && newAlgoFlag.enabled) {
            // New algorithm (A/B test variant)
            recommendations = applyNewAlgorithm(event);
            LOG.info("🆕 Using new algorithm for user {}", event.userId);
        } else {
            // Old algorithm (baseline)
            recommendations = applyOldAlgorithm(event);
        }

        if (personalizeFlag != null && personalizeFlag.enabled) {
            // Apply personalization
            recommendations = personalizeRecommendations(event, recommendations);
        }

        Recommendation rec = new Recommendation();
        rec.userId = event.userId;
        rec.recommendedProducts = recommendations;

        out.collect(rec);
    }

    @Override
    public void processBroadcastElement(FeatureFlag flag, Context ctx, Collector<Recommendation> out)
            throws Exception {

        BroadcastState<String, FeatureFlag> flags = ctx.getBroadcastState(flagStateDescriptor);
        flags.put(flag.flagName, flag);

        LOG.info("🚩 Feature flag '{}' set to: {}", flag.flagName, flag.enabled);
    }
}

// Update feature flags dynamically
FeatureFlag enableNewAlgo = new FeatureFlag("use-new-recommendation-algorithm", true);
// Send to Kafka topic "feature-flags" → All tasks update immediately!
```

**Benefits:**
- Change behavior without restarting job
- A/B testing: Enable for 50% of traffic
- Kill switch: Disable buggy feature instantly
- Gradual rollout: Enable for 10% → 50% → 100%

---

## ⚠️ Common Pitfalls

### 1. **Modifying Broadcast State in processElement()**

❌ **Problem:**
```java
@Override
public void processElement(UserEvent event, ReadOnlyContext ctx, ...) {
    // Compile error: ctx.getBroadcastState() returns ReadOnlyBroadcastState
    BroadcastState<String, MLModel> state = ctx.getBroadcastState(descriptor);
    state.put("key", value);  // ❌ COMPILE ERROR
}
```

✅ **Solution:**
```java
// Can ONLY read in processElement()
ReadOnlyBroadcastState<String, MLModel> state = ctx.getBroadcastState(descriptor);
MLModel model = state.get("current-model");  // ✅ Read only

// Can ONLY write in processBroadcastElement()
BroadcastState<String, MLModel> state = ctx.getBroadcastState(descriptor);
state.put("current-model", newModel);  // ✅ Write allowed
```

**Why this restriction?**
- Ensures all tasks have same broadcast state
- If task 1 modifies state, task 2 wouldn't see it
- Flink enforces consistency by making it read-only in main stream

---

### 2. **Broadcasting Large Data**

❌ **Problem:**
```java
// Broadcasting 500 MB ML model to 100 parallel tasks
// = 50 GB total memory usage!
BroadcastStream<HugeModel> broadcast = hugeModelStream
    .broadcast(descriptor);
```

✅ **Solution:**

**Option 1:** Use distributed cache
```java
// Load model from distributed file system (HDFS, S3)
env.registerCachedFile("s3://models/model-v2.bin", "ml-model");

// Each task loads from cache (shared storage)
public void open(Configuration config) {
    File modelFile = getRuntimeContext().getDistributedCache().getFile("ml-model");
    model = loadModel(modelFile);
}
```

**Option 2:** Broadcast model metadata, load actual model from storage
```java
public class ModelMetadata {
    String modelPath;  // "s3://models/model-v2.bin"
    int version;
    String checksum;
}

// Broadcast only metadata (few KB)
BroadcastStream<ModelMetadata> broadcast = modelMetadataStream
    .broadcast(descriptor);

// In processBroadcastElement:
@Override
public void processBroadcastElement(ModelMetadata metadata, Context ctx, ...) {
    // Download actual model from S3/HDFS
    MLModel model = downloadModelFromS3(metadata.modelPath);
    // Cache locally
    this.cachedModel = model;
}
```

**Rule of Thumb:**
- Broadcast data < 10 MB: ✅ Safe
- Broadcast data 10-100 MB: ⚠️ Risky (depends on parallelism)
- Broadcast data > 100 MB: ❌ Use distributed cache or external storage

---

### 3. **Not Handling Missing Broadcast State**

❌ **Problem:**
```java
@Override
public void processElement(UserEvent event, ReadOnlyContext ctx, ...) {
    MLModel model = ctx.getBroadcastState(descriptor).get("current-model");

    // NullPointerException if model not loaded yet!
    List<String> recs = model.predict(event);
}
```

✅ **Solution:**
```java
@Override
public void processElement(UserEvent event, ReadOnlyContext ctx, ...) {
    MLModel model = ctx.getBroadcastState(descriptor).get("current-model");

    if (model == null) {
        LOG.warn("No model loaded yet, using default recommendations");
        // Option 1: Use default recommendations
        out.collect(getDefaultRecommendations(event));
        // Option 2: Skip event
        return;
        // Option 3: Side output for later reprocessing
        ctx.output(UNPROCESSED_TAG, event);
    }

    List<String> recs = model.predict(event);
    out.collect(recs);
}
```

**Best practice:**
- Bootstrap broadcast state at job start (send initial model before main stream)
- Always check for null
- Have fallback logic for missing state

---

### 4. **Forgetting Broadcast State is Per-Task**

❌ **Misunderstanding:**
"Broadcast state is global - one copy across the entire job"

✅ **Reality:**
"Broadcast state is replicated - each parallel task has its own copy"

**Example:**
```
3 parallel tasks = 3 copies of the model

Task 0: Model v2 (10 MB)
Task 1: Model v2 (10 MB)  ← Same content, separate copy
Task 2: Model v2 (10 MB)

Total memory: 30 MB (not 10 MB!)
```

**Impact:**
- Memory scales with parallelism
- Model update sent to every task (network amplification)
- Each task processes independently with its copy

**Benefit:**
- No network calls during prediction (local access)
- No contention (each task has its own copy)

---

### 5. **Using Broadcast for High-Frequency Updates**

❌ **Problem:**
```java
// Updating prices 1000s of times per second via broadcast
// = Every task receives 1000s of updates per second!
BroadcastStream<PriceUpdate> priceBroadcast = priceUpdates
    .broadcast(descriptor);  // ❌ Network overload
```

✅ **Solution:**
```java
// Use keyed state for high-frequency updates
DataStream<PriceUpdate> priceUpdates = ...;

productStream
    .keyBy(product -> product.id)
    .connect(priceUpdates.keyBy(price -> price.productId))
    .process(new KeyedCoProcessFunction<String, Product, PriceUpdate, EnrichedProduct>() {

        private ValueState<Double> priceState;  // Per-key, not broadcast

        @Override
        public void processElement1(Product product, Context ctx, ...) {
            Double currentPrice = priceState.value();
            // Use current price
        }

        @Override
        public void processElement2(PriceUpdate price, Context ctx, ...) {
            priceState.update(price.newPrice);  // Update per-key
        }
    });
```

**When to use broadcast:**
- Updates: < 100/sec ✅
- Updates: 100-1000/sec ⚠️ (consider keyed state)
- Updates: > 1000/sec ❌ (definitely use keyed state)

---

## 🚀 Performance Tips

### 1. **Choose Right Parallelism**

```java
// Broadcast state replicates to ALL tasks
env.setParallelism(10);  // 10 copies of model
env.setParallelism(100); // 100 copies of model (10x memory!)

// Rule: Higher parallelism for main stream throughput
//       But: More memory for broadcast state
```

**Optimal parallelism:**
- Calculate: (main stream throughput) / (desired latency)
- Balance: Processing power vs memory usage
- Monitor: Memory per task manager

---

### 2. **Minimize Broadcast State Size**

❌ **Inefficient:**
```java
// Broadcast entire product catalog (100 MB)
BroadcastStream<Map<String, Product>> catalog = catalogStream
    .broadcast(descriptor);
```

✅ **Efficient:**
```java
// Broadcast only product ID → category mapping (1 MB)
BroadcastStream<Map<String, String>> categoryMapping = mappingStream
    .broadcast(descriptor);

// Fetch full product details from database when needed
// (or use Flink's Async I/O for efficient lookups)
```

---

### 3. **Batch Broadcast Updates**

❌ **Inefficient:**
```java
// Sending 1000 individual feature flag updates
for (FeatureFlag flag : flags) {
    sendToBroadcast(flag);  // 1000 network calls to each task!
}
```

✅ **Efficient:**
```java
// Batch all flags into one update
Map<String, FeatureFlag> allFlags = new HashMap<>();
for (FeatureFlag flag : flags) {
    allFlags.put(flag.name, flag);
}

// Send one batched update
sendToBroadcast(allFlags);  // 1 network call to each task
```

---

### 4. **Use Broadcast for Read-Heavy Workloads**

✅ **Good use case:**
```
Reads: 1,000,000 per second (main stream events)
Writes: 1 per hour (model updates)

Ratio: 3,600,000,000 reads : 1 write

Perfect for broadcast state! (optimize for reads)
```

❌ **Bad use case:**
```
Reads: 100 per second
Writes: 100 per second

Ratio: 1:1

Use keyed state instead! (too many broadcast updates)
```

---

## 🌍 Real-World Applications

### 1. **ML Model Serving (This Example!)**

**Use Case:** Real-time product recommendations

**Architecture:**
```
Training Pipeline (Offline):
  Historical data → Train model → Model v2.pkl
                                       ↓
                            Upload to Kafka topic "ml-models"
                                       ↓
Flink Job (Online):                    ↓
  User events ──┐                      │
                ├─→ BroadcastConnect ──┴─→ Recommendations
  ML models ────┘       ▲
                        └─ Each task caches model locally
```

**Benefits:**
- Model updates without restarting job
- Low latency inference (local model access)
- A/B testing different models (use model version in state key)

---

### 2. **Configuration Management**

**Use Case:** Dynamic application configuration

**Example:**
```java
public class AppConfig implements Serializable {
    public int maxRecommendations = 5;
    public double relevanceThreshold = 0.7;
    public boolean enablePersonalization = true;
    public String experimentVariant = "A";
}

// Broadcast config updates
BroadcastStream<AppConfig> configBroadcast = configStream
    .broadcast(configDescriptor);

// Apply config
@Override
public void processElement(UserEvent event, ReadOnlyContext ctx, ...) {
    AppConfig config = ctx.getBroadcastState(configDescriptor).get("config");

    if (config.experimentVariant.equals("A")) {
        // Variant A logic
    } else {
        // Variant B logic
    }

    List<String> recs = generateRecommendations(event, config.maxRecommendations);
    // ...
}
```

**Use Cases:**
- A/B testing parameters
- Circuit breakers (disable feature if error rate high)
- Rate limiting thresholds
- Business rules

---

### 3. **Dimension Table Enrichment**

**Use Case:** Enrich events with product catalog data

**Traditional Approach (Slow):**
```java
// Join with catalog stream (requires shuffle!)
productEvents
    .join(productCatalog)
    .where(event -> event.productId)
    .equalTo(product -> product.id)
    // Shuffle both streams = slow
```

**Broadcast Approach (Fast):**
```java
// Broadcast small product catalog
BroadcastStream<Map<String, Product>> catalogBroadcast = productCatalog
    .broadcast(catalogDescriptor);

productEvents
    .connect(catalogBroadcast)
    .process(new BroadcastProcessFunction<Event, Map<String, Product>, EnrichedEvent>() {

        @Override
        public void processElement(Event event, ReadOnlyContext ctx, ...) {
            Map<String, Product> catalog = ctx.getBroadcastState(descriptor).get("catalog");
            Product product = catalog.get(event.productId);

            EnrichedEvent enriched = new EnrichedEvent();
            enriched.event = event;
            enriched.productName = product.name;
            enriched.productCategory = product.category;
            enriched.productPrice = product.price;

            out.collect(enriched);
        }
    });
```

**Benefits:**
- No shuffle (broadcast sent directly to all tasks)
- Local lookup (O(1) HashMap access)
- Updates propagate automatically

**When this works:**
- Catalog is small (< 100 MB)
- Updates are infrequent (hourly/daily)
- Main stream is high-throughput

---

### 4. **Fraud Detection Rules**

**Use Case:** Dynamic fraud detection rules

**Example:**
```java
public class FraudRule implements Serializable {
    public String ruleId;
    public String condition;  // e.g., "amount > 1000 AND country = 'NG'"
    public String action;     // "BLOCK" or "REVIEW"
    public double priority;
}

// Broadcast fraud rules
BroadcastStream<FraudRule> rulesBroadcast = fraudRulesStream
    .broadcast(rulesDescriptor);

// Evaluate transactions against rules
@Override
public void processElement(Transaction tx, ReadOnlyContext ctx, ...) {
    ReadOnlyBroadcastState<String, FraudRule> rules = ctx.getBroadcastState(rulesDescriptor);

    for (Map.Entry<String, FraudRule> entry : rules.immutableEntries()) {
        FraudRule rule = entry.getValue();

        if (evaluateRule(rule, tx)) {
            if (rule.action.equals("BLOCK")) {
                out.collect(new Alert(tx, "BLOCKED", rule.ruleId));
                return;
            } else if (rule.action.equals("REVIEW")) {
                ctx.output(REVIEW_TAG, tx);
            }
        }
    }

    // No rules matched - allow transaction
    out.collect(new Alert(tx, "ALLOWED", null));
}

// Update rules dynamically
FraudRule newRule = new FraudRule();
newRule.ruleId = "RULE_123";
newRule.condition = "amount > 5000 AND ip_country != card_country";
newRule.action = "BLOCK";
// Send to Kafka → All tasks update rules immediately!
```

**Benefits:**
- Add new fraud rules without restarting job
- Remove false-positive rules instantly
- Adjust priorities based on evolving threats

---

### 5. **User Segmentation**

**Use Case:** Broadcast user segments for personalization

**Example:**
```java
public class UserSegment implements Serializable {
    public String userId;
    public String segment;  // "VIP", "REGULAR", "NEW", "CHURNING"
    public double lifetimeValue;
    public List<String> interests;
}

// Broadcast user segments (refreshed daily)
BroadcastStream<Map<String, UserSegment>> segmentBroadcast = userSegmentsStream
    .broadcast(segmentDescriptor);

// Personalize recommendations based on segment
@Override
public void processElement(UserEvent event, ReadOnlyContext ctx, ...) {
    Map<String, UserSegment> segments = ctx.getBroadcastState(descriptor).get("segments");
    UserSegment userSegment = segments.get(event.userId);

    if (userSegment == null) {
        userSegment = new UserSegment();
        userSegment.segment = "NEW";  // Default segment
    }

    List<String> recs;
    switch (userSegment.segment) {
        case "VIP":
            recs = getVIPRecommendations(event);  // Premium products
            break;
        case "CHURNING":
            recs = getRetentionRecommendations(event);  // Discounts
            break;
        default:
            recs = getStandardRecommendations(event);
    }

    out.collect(new Recommendation(event.userId, recs, userSegment.segment));
}
```

**Benefits:**
- Segment-based personalization
- Update segments overnight (batch job → Kafka)
- All tasks get updated segments simultaneously

---

## 📊 Monitoring & Debugging

### Metrics to Track

```java
// In BroadcastProcessFunction:
public class RecommendationEnricher extends BroadcastProcessFunction<...> {

    private transient Counter broadcastUpdatesCounter;
    private transient Gauge<Integer> modelVersionGauge;
    private transient Histogram predictionLatencyHistogram;

    @Override
    public void open(Configuration config) {
        broadcastUpdatesCounter = getRuntimeContext()
            .getMetricGroup()
            .counter("broadcast_updates");

        modelVersionGauge = getRuntimeContext()
            .getMetricGroup()
            .gauge("current_model_version", () -> {
                try {
                    MLModel model = broadcastState.get("current-model");
                    return model != null ? model.version : 0;
                } catch (Exception e) {
                    return -1;
                }
            });

        predictionLatencyHistogram = getRuntimeContext()
            .getMetricGroup()
            .histogram("prediction_latency_ms", new DescriptiveStatisticsHistogram(100));
    }

    @Override
    public void processBroadcastElement(MLModel model, Context ctx, ...) {
        broadcastUpdatesCounter.inc();
        // Update model...
    }

    @Override
    public void processElement(UserEvent event, ReadOnlyContext ctx, ...) {
        long startTime = System.currentTimeMillis();

        // Apply model...

        long latency = System.currentTimeMillis() - startTime;
        predictionLatencyHistogram.update(latency);
    }
}
```

**Key metrics:**
- `broadcast_updates`: How many times broadcast state was updated
- `current_model_version`: Which model version is active
- `prediction_latency_ms`: How long predictions take
- `broadcast_state_size_bytes`: Size of broadcast state in memory

---

## ❓ Quiz Questions

### Question 1: Read-Only vs Read-Write

In which method can you modify broadcast state?

A) `processElement()`
B) `processBroadcastElement()`
C) Both
D) Neither

<details>
<summary>💡 Click to see answer</summary>

**Answer: B) processBroadcastElement()**

**Explanation:**
- `processElement()` receives `ReadOnlyContext` → can only READ broadcast state
- `processBroadcastElement()` receives full `Context` → can READ and WRITE broadcast state

**Why this restriction?**
- Ensures all parallel tasks have identical broadcast state
- If task 1 modified state during `processElement()`, task 2 wouldn't see the change
- Flink enforces consistency by allowing writes only when processing broadcast elements (which go to ALL tasks)
</details>

---

### Question 2: Memory Usage

Job has 10 parallel tasks. Broadcast state contains 50 MB model. Total memory?

A) 50 MB
B) 500 MB
C) 5 MB
D) Depends on data

<details>
<summary>💡 Click to see answer</summary>

**Answer: B) 500 MB**

**Explanation:**
- Broadcast state is **replicated** to each parallel task
- Each task has its own copy (not shared)
- 10 tasks × 50 MB = 500 MB total

**Formula:**
```
Total memory = (broadcast state size) × (parallelism)
```

**Impact:**
- Higher parallelism = more memory usage
- Large broadcast state = exponential memory growth
- Always monitor memory per task manager
</details>

---

### Question 3: When to Use Broadcast State

Which use case is BEST for broadcast state?

A) User profile updates (1000s/sec)
B) ML model updates (1/hour)
C) Product price changes (100s/sec)
D) Order transactions (10000s/sec)

<details>
<summary>💡 Click to see answer</summary>

**Answer: B) ML model updates (1/hour)**

**Explanation:**

**Broadcast state is ideal for:**
- ✅ Low-frequency updates (hourly/daily)
- ✅ All tasks need same data (model, config)
- ✅ Small-to-medium size (< 100 MB)

**Analysis of options:**
- A) User profiles: 1000s/sec = too frequent, use keyed state
- B) ML model: 1/hour = perfect! ✅
- C) Prices: 100s/sec = too frequent, use keyed state
- D) Orders: 10000s/sec = main stream, not broadcast

**Rule of thumb:**
- Updates < 100/sec: Consider broadcast state
- Updates > 100/sec: Use keyed state instead
</details>

---

### Question 4: Broadcast vs Keyed State

What's the key difference?

A) Broadcast is faster
B) Broadcast replicates to all tasks, keyed partitions by key
C) Keyed state is deprecated
D) They're the same

<details>
<summary>💡 Click to see answer</summary>

**Answer: B) Broadcast replicates to all tasks, keyed partitions by key**

**Explanation:**

**Keyed State:**
```
User events partitioned by user ID:
Task 1: Users A, B, C (different state per user)
Task 2: Users D, E, F
Task 3: Users G, H, I
```

**Broadcast State:**
```
ML model broadcast to all tasks:
Task 1: Model v2 (same model)
Task 2: Model v2 (same model)
Task 3: Model v2 (same model)
```

**When to use:**
- Keyed: Per-entity state (user profiles, product inventory)
- Broadcast: Shared state (ML models, configs, rules)
</details>

---

### Question 5: Initial State

What happens if main stream processes events before broadcast stream sends data?

A) Job crashes
B) Events are buffered
C) Events are dropped
D) Depends on your code

<details>
<summary>💡 Click to see answer</summary>

**Answer: D) Depends on your code**

**Explanation:**

**If you don't handle null:**
```java
MLModel model = broadcastState.get("current-model");
List<String> recs = model.predict(event);  // NullPointerException!
```

**If you handle null:**
```java
MLModel model = broadcastState.get("current-model");

if (model == null) {
    LOG.warn("No model loaded yet");
    return;  // Skip event
    // OR: Use default recommendations
    // OR: Buffer for later processing
}

List<String> recs = model.predict(event);
```

**Best practice:**
- Bootstrap broadcast state at job start
- Always check for null
- Have fallback logic (default values, skip, etc.)
</details>

---

## 🎯 Summary

### Key Takeaways

✅ **Broadcast state** replicates low-throughput data to ALL parallel tasks

✅ **Two streams:** Main (high-throughput) + Broadcast (low-throughput)

✅ **Access control:** Read-only in main stream, read-write in broadcast stream

✅ **Memory:** Scales with parallelism (N tasks = N copies)

✅ **Use cases:** ML models, configs, feature flags, dimension tables, fraud rules

✅ **Performance:** Optimize for read-heavy workloads (reads >> writes)

✅ **Size limit:** Keep broadcast state < 100 MB per task

---

### Next Steps

1. ✅ **Run the example:** `BroadcastStateExample.java`
2. ✅ **Complete exercises:** Model metrics, multi-model, feature flags
3. 📚 **Learn more patterns:**
   - Pattern 01: Session Windows (user session tracking)
   - Pattern 03: CEP (complex event sequences)
4. 🔗 **Combine patterns:** See `BasketAnalysisJob.java` for pattern composition

---

## 📚 Further Reading

- [Flink Broadcast State Docs](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/fault-tolerance/broadcast_state/)
- [BroadcastProcessFunction API](https://nightlies.apache.org/flink/flink-docs-release-1.20/api/java/org/apache/flink/streaming/api/functions/co/BroadcastProcessFunction.html)
- [State Backends](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/ops/state/state_backends/)

---

<div align="center">
  <b>🎓 Pattern 02: Broadcast State - Complete!</b><br>
  <i>Next: Pattern 03 - CEP for Complex Event Detection</i>
</div>
