# Flink Pattern: ML Classification with Hybrid Sources & Side Outputs

## 🎯 Learning Objectives

After completing this module, you will understand:
1. How to combine hybrid sources (bounded → unbounded) with ML classification
2. How to implement logistic regression for binary classification in Flink
3. How to use side outputs to route classified results to different streams
4. How to bootstrap ML models with historical data before processing live streams
5. Performance considerations for ML inference in streaming pipelines

## 📖 Pattern Overview

### What is This Pattern?

This pattern combines three powerful Flink capabilities:
- **Hybrid Sources** - Bootstrap from historical data (file), then switch to real-time (Kafka)
- **ML Classification** - Use logistic regression to classify streaming events
- **Side Outputs** - Route classified results to different processing pipelines

This is essential for:
- **Fraud detection** - Classify transactions as fraud/legitimate
- **User segmentation** - Classify users as high-value/low-value
- **Quality control** - Classify products as defective/good
- **Churn prediction** - Classify customers as likely-to-churn/stable
- **Lead scoring** - Classify leads as hot/cold

### Visual Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    HYBRID SOURCE                             │
│                                                              │
│  ┌──────────────────┐         ┌───────────────────┐        │
│  │  Historical Data │  ──→    │   Live Kafka      │        │
│  │  (File/Paimon)   │         │   Stream          │        │
│  │  BOUNDED         │         │   UNBOUNDED       │        │
│  └──────────────────┘         └───────────────────┘        │
└─────────────────┬──────────────────────────────────────────┘
                  │
                  │ Purchase Events (features)
                  ▼
┌─────────────────────────────────────────────────────────────┐
│        ML CLASSIFICATION (ProcessFunction)                   │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │  Logistic Regression Classifier                     │    │
│  │                                                      │    │
│  │  P(high_value) = σ(w₀ + w₁x₁ + w₂x₂ + ... + wₙxₙ) │    │
│  │                                                      │    │
│  │  Features: basket_size, total_value, user_history   │    │
│  └────────────────────────────────────────────────────┘    │
│                                                              │
│  Decision: if P(high_value) > 0.5 → HIGH_VALUE              │
│           else                    → STANDARD                 │
└──────────────────┬──────────────────┬────────────────────────┘
                   │                  │
     ┌─────────────┴──────┐          └──────────────────┐
     │                    │                             │
     ▼                    ▼                             ▼
┌──────────────┐   ┌──────────────┐         ┌──────────────────┐
│ HIGH VALUE   │   │  STANDARD    │         │  PARSE ERRORS    │
│ Side Output  │   │  Main Output │         │  Side Output     │
│              │   │              │         │                  │
│ P > 0.5      │   │  P ≤ 0.5     │         │  Invalid data    │
└──────┬───────┘   └──────┬───────┘         └──────┬───────────┘
       │                  │                         │
       ▼                  ▼                         ▼
┌──────────────┐   ┌──────────────┐         ┌──────────────────┐
│ VIP Pipeline │   │ Standard     │         │ Dead Letter      │
│ - Discounts  │   │ Processing   │         │ Queue            │
│ - Priority   │   │              │         │                  │
└──────────────┘   └──────────────┘         └──────────────────┘
```

## 🔑 Key Concepts

### 1. Hybrid Source for ML Bootstrap

**Why?** ML models often need historical context before making predictions on live data.

```java
// STEP 1: Load historical training data from file
FileSource<String> historicalData = FileSource
    .forRecordStreamFormat(new TextLineInputFormat(),
                          new Path("data/historical-purchases.jsonl"))
    .build();

// STEP 2: Create live Kafka source
KafkaSource<String> liveData = KafkaSource.<String>builder()
    .setBootstrapServers(bootstrapServers)
    .setTopics("purchase-events")
    .setStartingOffsets(OffsetsInitializer.latest())
    .setValueOnlyDeserializer(new SimpleStringSchema())
    .build();

// STEP 3: Combine into hybrid source
HybridSource<String> source = HybridSource.builder(historicalData)
    .addSource(liveData)
    .build();
```

### 2. Logistic Regression for Binary Classification

**What is Logistic Regression?**

Logistic regression is a statistical model for binary classification:

```
P(y=1|x) = σ(w₀ + w₁x₁ + w₂x₂ + ... + wₙxₙ)

where σ(z) = 1 / (1 + e^(-z))  [sigmoid function]
```

**Example:**
```
Features (x):
  x₁ = basket_size (normalized 0-1)
  x₂ = total_value (normalized 0-1)
  x₃ = user_purchase_history (count)

Weights (w):
  w₀ = -2.5  (bias)
  w₁ = 1.8   (basket_size weight)
  w₂ = 3.2   (total_value weight)
  w₃ = 0.9   (history weight)

For purchase: basket_size=5, total_value=$250, history=10
  Normalized: x₁=0.5, x₂=0.7, x₃=10

  z = -2.5 + (1.8 × 0.5) + (3.2 × 0.7) + (0.9 × 10)
    = -2.5 + 0.9 + 2.24 + 9.0
    = 9.64

  P(high_value) = σ(9.64) = 1 / (1 + e^(-9.64)) ≈ 0.9999

  Decision: HIGH VALUE ✓
```

### 3. Side Outputs for Classified Results

**Why Side Outputs?** Different classifications need different processing:

```java
// Define output tags for each classification
public static final OutputTag<ClassifiedPurchase> HIGH_VALUE_TAG =
    new OutputTag<ClassifiedPurchase>("high-value") {};

public static final OutputTag<ErrorEvent> PARSE_ERROR_TAG =
    new OutputTag<ErrorEvent>("parse-errors") {};

// In processElement()
if (probability > 0.5) {
    // Route to high-value side output
    ctx.output(HIGH_VALUE_TAG, classifiedPurchase);
} else {
    // Route to standard main output
    out.collect(classifiedPurchase);
}
```

## 💻 Complete Implementation

### Data Models

```java
// Input: Purchase event
public static class PurchaseEvent {
    public String userId;
    public String sessionId;
    public List<String> items;
    public double totalValue;
    public int basketSize;
    public long timestamp;
}

// Output: Classified purchase with ML prediction
public static class ClassifiedPurchase {
    public PurchaseEvent purchase;
    public String classification;  // "HIGH_VALUE" or "STANDARD"
    public double probability;     // 0.0 - 1.0
    public Map<String, Double> features;  // For explainability
}

// Side output: Errors
public static class ErrorEvent {
    public String rawData;
    public String errorType;
    public String errorMessage;
    public long timestamp;
}
```

### Logistic Regression Implementation

```java
public static class LogisticRegressionClassifier implements Serializable {

    // Model weights (learned from training data)
    private final double[] weights;
    private final double bias;

    // Feature normalization parameters
    private final double[] featureMeans;
    private final double[] featureStdDevs;

    public LogisticRegressionClassifier(double[] weights, double bias,
                                       double[] means, double[] stdDevs) {
        this.weights = weights;
        this.bias = bias;
        this.featureMeans = means;
        this.featureStdDevs = stdDevs;
    }

    /**
     * Extract features from purchase event
     */
    public double[] extractFeatures(PurchaseEvent purchase) {
        return new double[] {
            purchase.basketSize,      // x₁: Number of items
            purchase.totalValue,      // x₂: Total purchase value
            getUserHistory(purchase)  // x₃: Historical purchase count
        };
    }

    /**
     * Normalize features using z-score normalization
     */
    private double[] normalizeFeatures(double[] features) {
        double[] normalized = new double[features.length];
        for (int i = 0; i < features.length; i++) {
            normalized[i] = (features[i] - featureMeans[i]) / featureStdDevs[i];
        }
        return normalized;
    }

    /**
     * Compute logistic regression prediction
     * Returns probability of being "HIGH_VALUE"
     */
    public double predict(PurchaseEvent purchase) {
        double[] features = extractFeatures(purchase);
        double[] normalized = normalizeFeatures(features);

        // Compute z = w₀ + w₁x₁ + w₂x₂ + ... + wₙxₙ
        double z = bias;
        for (int i = 0; i < weights.length; i++) {
            z += weights[i] * normalized[i];
        }

        // Apply sigmoid: σ(z) = 1 / (1 + e^(-z))
        return sigmoid(z);
    }

    /**
     * Sigmoid activation function
     */
    private double sigmoid(double z) {
        return 1.0 / (1.0 + Math.exp(-z));
    }

    /**
     * Classify purchase based on threshold (default 0.5)
     */
    public String classify(PurchaseEvent purchase, double threshold) {
        double probability = predict(purchase);
        return probability >= threshold ? "HIGH_VALUE" : "STANDARD";
    }
}
```

### ProcessFunction with Classification

```java
public static class MLClassificationProcessor
        extends ProcessFunction<String, ClassifiedPurchase> {

    private transient LogisticRegressionClassifier classifier;
    private transient ObjectMapper objectMapper;

    @Override
    public void open(Configuration parameters) {
        // Initialize classifier with pre-trained weights
        // In production, load from model registry or state
        double[] weights = {1.8, 3.2, 0.9};  // Feature weights
        double bias = -2.5;
        double[] means = {3.0, 150.0, 5.0};   // Feature means
        double[] stdDevs = {2.0, 100.0, 3.0}; // Feature std devs

        classifier = new LogisticRegressionClassifier(
            weights, bias, means, stdDevs
        );

        objectMapper = new ObjectMapper();
    }

    @Override
    public void processElement(String value, Context ctx,
                              Collector<ClassifiedPurchase> out) {
        try {
            // Parse purchase event
            PurchaseEvent purchase = objectMapper.readValue(
                value, PurchaseEvent.class
            );

            // Validate data
            if (purchase.totalValue <= 0 || purchase.basketSize <= 0) {
                ErrorEvent error = new ErrorEvent();
                error.rawData = value;
                error.errorType = "INVALID_DATA";
                error.errorMessage = "Invalid purchase values";
                error.timestamp = System.currentTimeMillis();

                ctx.output(PARSE_ERROR_TAG, error);
                return;
            }

            // Make prediction
            double probability = classifier.predict(purchase);
            String classification = classifier.classify(purchase, 0.5);

            // Create classified result
            ClassifiedPurchase result = new ClassifiedPurchase();
            result.purchase = purchase;
            result.classification = classification;
            result.probability = probability;
            result.features = extractFeatureMap(purchase);

            // Route based on classification
            if (classification.equals("HIGH_VALUE")) {
                // HIGH_VALUE → side output for VIP treatment
                ctx.output(HIGH_VALUE_TAG, result);
            } else {
                // STANDARD → main output for normal processing
                out.collect(result);
            }

        } catch (Exception e) {
            // Handle parse errors
            ErrorEvent error = new ErrorEvent();
            error.rawData = value;
            error.errorType = "PARSE_ERROR";
            error.errorMessage = e.getMessage();
            error.timestamp = System.currentTimeMillis();

            ctx.output(PARSE_ERROR_TAG, error);
        }
    }

    private Map<String, Double> extractFeatureMap(PurchaseEvent purchase) {
        Map<String, Double> features = new HashMap<>();
        features.put("basket_size", (double) purchase.basketSize);
        features.put("total_value", purchase.totalValue);
        features.put("user_history", (double) getUserHistory(purchase));
        return features;
    }
}
```

### Main Job Assembly

```java
public static void main(String[] args) throws Exception {
    StreamExecutionEnvironment env =
        StreamExecutionEnvironment.getExecutionEnvironment();

    String bootstrapServers =
        System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:19092");
    String historicalFile =
        System.getenv().getOrDefault("HISTORICAL_DATA",
                                    "data/historical-purchases.jsonl");

    // Create hybrid source: Historical → Live
    HybridSource<String> source = createHybridSource(historicalFile, bootstrapServers);

    DataStream<String> purchaseStream = env.fromSource(
        source,
        WatermarkStrategy.noWatermarks(),
        "Purchase Hybrid Source"
    );

    // Apply ML classification with side outputs
    SingleOutputStreamOperator<ClassifiedPurchase> standardStream = purchaseStream
        .process(new MLClassificationProcessor())
        .name("ML Classification");

    // Extract side outputs
    DataStream<ClassifiedPurchase> highValueStream =
        standardStream.getSideOutput(HIGH_VALUE_TAG);

    DataStream<ErrorEvent> errorStream =
        standardStream.getSideOutput(PARSE_ERROR_TAG);

    // Process each stream independently

    // HIGH VALUE: VIP treatment
    highValueStream
        .map(cp -> {
            LOG.info("🌟 HIGH VALUE: User {} - ${} ({}% confidence)",
                    cp.purchase.userId,
                    cp.purchase.totalValue,
                    (int)(cp.probability * 100));
            return cp;
        })
        .addSink(new HighValuePurchaseSink())
        .name("VIP Processing");

    // STANDARD: Normal processing
    standardStream
        .map(cp -> {
            LOG.info("✓ STANDARD: User {} - ${} ({}% confidence)",
                    cp.purchase.userId,
                    cp.purchase.totalValue,
                    (int)(cp.probability * 100));
            return cp;
        })
        .addSink(new StandardPurchaseSink())
        .name("Standard Processing");

    // ERRORS: Dead letter queue
    errorStream
        .map(e -> {
            LOG.error("❌ ERROR: {} - {}", e.errorType, e.errorMessage);
            return e;
        })
        .addSink(new ErrorSink())
        .name("Error Handling");

    env.execute("ML Classification with Hybrid Sources & Side Outputs");
}
```

## 🎓 Hands-On Exercises

### Exercise 1: Add Feature Engineering

**Task:** Enhance the classifier with more features.

**Requirements:**
1. Add `average_item_price` feature
2. Add `purchase_hour_of_day` feature (time-based pattern)
3. Add `category_diversity` feature (number of unique categories)
4. Update normalization parameters

**Hints:**
```java
public double[] extractFeatures(PurchaseEvent purchase) {
    return new double[] {
        purchase.basketSize,
        purchase.totalValue,
        getUserHistory(purchase),
        purchase.totalValue / purchase.basketSize,  // avg item price
        extractHourOfDay(purchase.timestamp),       // time pattern
        countUniqueCategories(purchase.items)       // diversity
    };
}
```

### Exercise 2: Multi-Class Classification

**Challenge:** Extend to 3 classes: HIGH_VALUE, MEDIUM_VALUE, LOW_VALUE

**Approach:**
- Use softmax instead of sigmoid
- Create 3 side outputs
- Implement one-vs-rest classification

### Exercise 3: Online Learning

**Advanced:** Implement online model updates as new data arrives.

**Requirements:**
1. Store model weights in keyed state
2. Update weights using gradient descent on each prediction
3. Apply model decay to prevent overfitting

```java
@Override
public void processElement(PurchaseEvent purchase, Context ctx,
                          Collector<ClassifiedPurchase> out) {
    // Make prediction with current weights
    double prediction = classifier.predict(purchase);

    // Get actual label (if available from downstream feedback)
    // Update weights using gradient descent
    if (hasGroundTruth(purchase)) {
        double actual = getActualLabel(purchase);
        classifier.updateWeights(purchase, actual, prediction);
    }

    // Continue with classification...
}
```

## 📊 Performance Considerations

### 1. Feature Extraction Overhead

**Benchmark:**
- Simple features (3-5): ~0.1ms per event
- Complex features (10+): ~1-2ms per event
- Database lookups: ~10-50ms per event

**Optimization:**
- Cache user history in state
- Precompute features in upstream operators
- Use async I/O for external lookups

### 2. Model Complexity

| Model Type | Inference Time | Throughput | Use Case |
|------------|---------------|------------|----------|
| Logistic Regression | <1ms | >100K/sec | Binary classification |
| Decision Tree | <2ms | >50K/sec | Multi-class, interpretable |
| Neural Network | 5-20ms | >10K/sec | Complex patterns |

**Recommendation:** Logistic regression is optimal for real-time streaming!

### 3. Parallelism

```java
env.setParallelism(4);  // Balance throughput vs latency

// For ML inference, higher parallelism = better throughput
purchaseStream
    .process(new MLClassificationProcessor())
    .setParallelism(8)  // More parallel classifiers
```

## 🔍 Common Pitfalls

### ❌ Pitfall 1: Not Normalizing Features

**Problem:**
```java
// ❌ WRONG: Features at different scales
basket_size: 1-10
total_value: 10-10000
history: 0-100

// Result: total_value dominates, other features ignored!
```

**Solution:**
```java
// ✅ CORRECT: Z-score normalization
normalized[i] = (feature[i] - mean[i]) / stdDev[i]
// Now all features are on similar scale (-3 to +3)
```

### ❌ Pitfall 2: Overfitting on Historical Data

**Problem:**
```java
// ❌ WRONG: Model trained only on historical data
// When live data arrives, distribution has shifted!
```

**Solution:**
```java
// ✅ CORRECT: Implement online learning or periodic retraining
// Monitor prediction accuracy
// Retrain when accuracy drops below threshold
```

### ❌ Pitfall 3: Feature Leakage

**Problem:**
```java
// ❌ WRONG: Using future information as feature
double[] features = {
    purchase.totalValue,
    purchase.returnedLater  // ⚠️ Not known at prediction time!
};
```

**Solution:**
```java
// ✅ CORRECT: Only use features available at prediction time
double[] features = {
    purchase.totalValue,
    purchase.basketSize,
    getUserHistoryBeforePurchase(purchase)  // Historical only
};
```

## 🔗 Real-World Use Cases

### 1. E-Commerce Fraud Detection

```
Purchase Events (Hybrid: Historical + Live)
    ↓
ML Classifier (Fraud/Legitimate)
    ↓
Side Outputs:
  - High Risk → Manual review queue
  - Medium Risk → Additional verification
  - Low Risk → Auto-approve (main output)
```

### 2. Customer Churn Prediction

```
User Activity Events
    ↓
Logistic Regression (Churn probability)
    ↓
Side Outputs:
  - High churn risk → Retention campaigns
  - Medium risk → Engagement emails
  - Low risk → Standard communications
```

### 3. Content Recommendation Quality

```
Content Interactions
    ↓
Quality Classifier (Good/Bad recommendation)
    ↓
Side Outputs:
  - Good recommendations → Boost similar content
  - Bad recommendations → Suppress similar patterns
  - Uncertain → A/B testing
```

## 📚 Further Reading

- [DeepNetts Documentation](https://github.com/deepnetts/deepnetts-communty)
- [Logistic Regression Theory](https://en.wikipedia.org/wiki/Logistic_regression)
- [Flink ML Documentation](https://nightlies.apache.org/flink/flink-ml-docs-stable/)
- [Feature Engineering Best Practices](https://developers.google.com/machine-learning/crash-course/representation/feature-engineering)

## ✅ Key Takeaways

1. **Hybrid sources** enable bootstrapping from historical data before live processing
2. **Logistic regression** provides fast, interpretable binary classification
3. **Side outputs** route classified results to different processing pipelines
4. **Feature normalization** is critical for model accuracy
5. **Model complexity** must balance accuracy vs inference latency
6. This pattern combines three Flink features to create production-ready ML pipelines

## 🎯 Next Steps

- **Pattern 05: Model Serving** - Deploy complex models with async I/O
- **Pattern 06: A/B Testing** - Compare model versions in production
- **Advanced: Online Learning** - Update models with streaming feedback

---

**Workshop Note:** This pattern is used by companies like Alibaba, Uber, and Netflix for real-time ML inference on billions of events per day. Mastering this pattern enables you to build production-grade ML streaming applications.
