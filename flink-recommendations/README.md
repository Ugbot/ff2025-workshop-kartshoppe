# Basket Analysis and Recommendation Flink Job

A sophisticated Apache Flink job that learns from actual shopping baskets to generate personalized product recommendations using machine learning and pattern mining.

## Overview

This job implements a **self-learning recommendation system** that:
- Tracks shopping baskets in real-time
- Learns patterns from completed purchases
- Generates recommendations based on discovered patterns
- Continuously improves through online learning

## Architecture

```
┌─────────────────┐     ┌──────────────┐     ┌─────────────────┐
│ Shopping Events │────▶│   Basket     │────▶│   Completed     │
│  (ADD_TO_CART,  │     │   Tracker    │     │    Baskets      │
│   VIEW, etc.)   │     │              │     │                 │
└─────────────────┘     └──────────────┘     └─────────────────┘
                               │                      │
                               ▼                      ▼
                        ┌──────────────┐     ┌─────────────────┐
                        │   Current    │     │    Pattern      │
                        │   Session    │     │     Miner       │
                        └──────────────┘     └─────────────────┘
                               │                      │
                               ▼                      ▼
                        ┌──────────────┐     ┌─────────────────┐
                        │Recommendation│◀────│    Learned      │
                        │  Generator   │     │    Patterns     │
                        └──────────────┘     └─────────────────┘
                               │
                               ▼
                        ┌──────────────┐
                        │Recommendations│
                        │  to Kafka     │
                        └──────────────┘
```

## Key Features

### 🛒 Real-time Basket Tracking
- Monitors shopping events (ADD_TO_CART, REMOVE_FROM_CART, VIEW_PRODUCT, PURCHASE)
- Maintains stateful basket per session
- Handles session timeouts (30 minutes)
- Tracks both current items and view history

### 🔍 Pattern Mining from Actual Purchases
- Learns from completed baskets (when users make purchases)
- Discovers association rules: {antecedents} → {consequent}
- Calculates key metrics:
  - **Support**: Frequency of pattern occurrence
  - **Confidence**: P(consequent | antecedents)
  - **Lift**: Strength of association
- Maintains historical baskets for pattern validation

### 🎯 Smart Recommendations
- **Basket-based**: Suggests complementary products when items are added to cart
- **View-based**: Recommends related products based on browsing
- **Cross-sell**: Identifies frequently bought together items
- Filters patterns by confidence threshold (>0.5 for basket, >0.4 for pairs)

### 🧠 Continuous Learning
- Every purchase improves the model
- No separate training data needed
- Adapts to changing customer behavior
- Maintains sliding window of recent baskets

## How It Works

### 1. Basket Tracking Phase
```java
Shopping Event → BasketTracker → Update State
                                → Emit BasketCompletion (on PURCHASE)
```

When a user shops:
- ADD_TO_CART: Adds item to basket state
- VIEW_PRODUCT: Records in view history
- PURCHASE: Emits completed basket for mining

### 2. Pattern Mining Phase
```java
BasketCompletion → PatternMiner → Extract Patterns
                                → Calculate Metrics
                                → Emit BasketPatterns
```

From a basket containing [A, B, C], generates patterns:
- {A, B} → C (confidence: 0.8)
- {A} → B (confidence: 0.6)
- {B} → C (confidence: 0.7)

### 3. Recommendation Generation
```java
Current Basket + Learned Patterns → RecommendationGenerator → Recommendations
```

Example:
- User adds "laptop" to cart
- System finds pattern: {laptop} → mouse (confidence: 0.85)
- Recommends "mouse" to user

## Data Models

### Input: EcommerceEvent
```json
{
  "sessionId": "sess_123",
  "userId": "user_456",
  "eventType": "ADD_TO_CART",
  "productId": "PROD_001",
  "productName": "Laptop",
  "category": "Electronics",
  "price": 999.99
}
```

### Intermediate: BasketCompletion
```json
{
  "sessionId": "sess_123",
  "userId": "user_456",
  "items": [
    {
      "productId": "PROD_001",
      "productName": "Laptop",
      "category": "Electronics",
      "price": 999.99,
      "quantity": 1
    }
  ],
  "totalValue": 999.99,
  "completionTime": 1234567890
}
```

### Pattern: BasketPattern
```json
{
  "antecedents": ["PROD_001", "PROD_002"],
  "consequent": "PROD_003",
  "support": 0.15,
  "confidence": 0.75,
  "lift": 3.2,
  "category": "Electronics"
}
```

### Output: RecommendationEvent
```json
{
  "sessionId": "sess_123",
  "userId": "user_456",
  "recommendationType": "BASKET_BASED",
  "recommendedProducts": ["PROD_003", "PROD_004"],
  "confidence": 0.75,
  "triggerEvent": "ADD_TO_CART",
  "context": {
    "basketSize": "2"
  }
}
```

## Configuration

### Environment Variables
```bash
# Kafka/Redpanda connection
KAFKA_BOOTSTRAP_SERVERS=localhost:19092

# Apache Paimon (for pattern storage)
PAIMON_WAREHOUSE=/tmp/paimon

# Pattern Mining Thresholds
MIN_SUPPORT=0.01        # Minimum 1% occurrence
MIN_CONFIDENCE=0.3      # Minimum 30% probability
MIN_LIFT=1.0           # Positive correlation only
```

### Kafka Topics

#### Input Topics
- `ecommerce-events`: Shopping events from frontend
- `shopping-cart-events`: Cart updates

#### Output Topics
- `product-recommendations`: Generated recommendations
- `basket-patterns`: Discovered patterns (for monitoring)
- `websocket_fanout`: Real-time UI updates

## Running the Job

### Prerequisites

1. **Build DeepNetts** (if using ML features):
```bash
cd libs/deepnetts-community/deepnetts-core
mvn clean install -DskipTests
```

2. **Create Kafka Topics**:
```bash
# Create input topics
rpk topic create ecommerce-events shopping-cart-events \
  --brokers localhost:19092

# Create output topics
rpk topic create product-recommendations basket-patterns websocket_fanout \
  --brokers localhost:19092
```

### Build and Run

```bash
# Build the job
./gradlew :flink-recommendations:shadowJar

# Run with Flink
flink run -c com.ververica.composable_job.flink.recommendations.BasketAnalysisJob \
  flink-recommendations/build/libs/flink-recommendations-all.jar

# Or run directly
java -cp flink-recommendations/build/libs/flink-recommendations-all.jar \
  com.ververica.composable_job.flink.recommendations.BasketAnalysisJob
```

## State Management

### Stateful Components

1. **BasketTracker State**
   - `basketState`: Current basket contents
   - `itemsState`: Detailed item information
   - `lastActivityState`: For session timeout

2. **PatternMiner State**
   - `historicalBasketsState`: Last 1000 completed baskets
   - `itemFrequencyState`: Product popularity
   - `basketCountState`: Total baskets processed

3. **RecommendationGenerator State**
   - `basketState`: Current session basket
   - `patternsState`: Last 5000 learned patterns

### Checkpointing
- Interval: 30 seconds
- State backend: RocksDB for production
- Exactly-once semantics

## Pattern Mining Algorithm

### Association Rule Generation

For a basket containing [A, B, C, D]:

1. **Generate all possible rules**:
   - {A, B, C} → D
   - {A, B} → C
   - {A} → B
   - etc.

2. **Calculate metrics**:
   ```
   Confidence = P(consequent | antecedents)
              = count(antecedents ∪ consequent) / count(antecedents)
   
   Support = P(antecedents ∪ consequent)
           = count(antecedents ∪ consequent) / total_baskets
   
   Lift = Confidence / P(consequent)
        = Confidence / (count(consequent) / total_baskets)
   ```

3. **Filter by thresholds**:
   - Full patterns: confidence > 0.3, support > 0.01
   - Pair patterns: confidence > 0.4

### Example Pattern Discovery

Given baskets:
```
Basket 1: [laptop, mouse, keyboard]
Basket 2: [laptop, mouse, monitor]
Basket 3: [laptop, keyboard]
```

Discovered patterns:
- {laptop} → mouse (confidence: 0.67, support: 0.67)
- {laptop} → keyboard (confidence: 0.67, support: 0.67)
- {laptop, mouse} → keyboard (confidence: 0.5, support: 0.33)

## Performance Tuning

### Parallelism
```java
env.setParallelism(4); // Adjust based on cluster
```

### Memory Configuration
```bash
# For large state
-Xmx4g -Xms2g
```

### State Size Management
- Historical baskets: Keep last 1000
- Learned patterns: Keep last 5000
- Auto-cleanup of expired sessions

### Kafka Optimization
```java
.setProperty("fetch.min.bytes", "1024")
.setProperty("fetch.max.wait.ms", "500")
```

## Monitoring

### Key Metrics

1. **Pattern Quality**
   - Average confidence of patterns
   - Pattern coverage (% of baskets with patterns)
   - Lift distribution

2. **Recommendation Performance**
   - Recommendations per minute
   - Click-through rate (if tracked)
   - Coverage (% of sessions with recommendations)

3. **System Health**
   - Checkpoint duration
   - State size growth
   - Processing latency

### Logging
```properties
log4j.logger.com.ververica.recommendations=DEBUG
log4j.logger.pattern.mining=INFO
```

## Testing

### Send Test Events

1. **Add to Cart**:
```bash
echo '{
  "sessionId": "test-session",
  "userId": "test-user",
  "eventType": "ADD_TO_CART",
  "productId": "PROD_001",
  "productName": "Laptop",
  "category": "Electronics",
  "price": 999.99
}' | rpk topic produce ecommerce-events
```

2. **Complete Purchase**:
```bash
echo '{
  "sessionId": "test-session",
  "userId": "test-user",
  "eventType": "PURCHASE"
}' | rpk topic produce ecommerce-events
```

3. **Check Recommendations**:
```bash
rpk topic consume product-recommendations --format json
```

4. **Monitor Patterns**:
```bash
rpk topic consume basket-patterns --format json
```

## Advanced Features

### ML Integration (ProductRecommender)

The system includes a `ProductRecommender` class that can:
- Use DeepNetts for neural network predictions
- Implement collaborative filtering
- Perform online learning
- Fall back to rule-based recommendations

### Pattern Interestingness

Patterns are scored by "interestingness":
```java
score = confidence * 0.4 + 
        min(lift/10, 0.3) + 
        min(support*10, 0.3)
```

### Temporal Patterns

Future enhancement: Consider time-based patterns:
- Morning purchases
- Weekend shopping behavior
- Seasonal patterns

## Troubleshooting

### Common Issues

1. **No recommendations generated**
   - Check if patterns exist: `rpk topic consume basket-patterns`
   - Verify confidence thresholds
   - Ensure enough historical data

2. **Poor recommendation quality**
   - Increase minimum confidence
   - Require higher support
   - Check data quality

3. **Memory issues**
   - Reduce historical basket limit
   - Decrease pattern retention
   - Enable incremental checkpoints

4. **Session timeout issues**
   - Adjust timeout duration (default 30 min)
   - Check timer service

## Production Considerations

### Scalability
- Partition by sessionId for basket tracking
- Partition by userId for pattern mining
- Use broadcast state for global patterns

### Data Quality
- Validate event schema
- Handle duplicate events
- Filter test/bot traffic

### Privacy
- Anonymize user IDs
- Aggregate patterns only
- Implement data retention policies

### A/B Testing
- Support multiple recommendation strategies
- Track performance metrics
- Enable feature flags

## Future Enhancements

1. **Advanced ML Models**
   - Deep learning for sequence prediction
   - Graph neural networks for product relationships
   - Reinforcement learning for long-term optimization

2. **Richer Patterns**
   - Sequential patterns (order matters)
   - Temporal patterns (time-aware)
   - Multi-level patterns (categories + products)

3. **Personalization**
   - User-specific patterns
   - Demographic-based recommendations
   - Context-aware suggestions (device, location)

4. **Real-time Experimentation**
   - Multi-armed bandits
   - Thompson sampling
   - Contextual bandits

## License

Part of the Ververica Visual Demo project showcasing advanced Flink capabilities for e-commerce recommendation systems.