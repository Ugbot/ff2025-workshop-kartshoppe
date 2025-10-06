# Basket Analysis → Real-Time Recommendations Demo

## Overview

This demo shows the complete flow from manually adding items to your shopping cart to receiving AI-powered product recommendations from the Flink basket analysis job.

## Architecture

```
Frontend (React)
    │
    ├─ Add items to cart
    │
    ▼
Quarkus API
    │
    ├─ Publish to Kafka: ecommerce-events
    │
    ▼
Flink Basket Analysis Job (BasketAnalysisJobRefactored)
    │
    ├─ Pattern 01: Keyed State + Timers (BasketTrackerFunction)
    │   └─ Track basket per session
    │
    ├─ Pattern 02: Association Rule Mining (PatternMinerFunction)
    │   └─ Mine patterns: laptop → mouse (confidence: 0.75)
    │
    ├─ Pattern 03: Broadcast State (RecommendationGeneratorFunction)
    │   └─ Apply patterns to generate recommendations
    │
    ├─ Publish to Kafka: product-recommendations
    │
    ▼
Quarkus API (RecommendationConsumer)
    │
    ├─ Forward to WebSocket
    │
    ▼
Frontend (RecommendationsSection)
    │
    └─ Display: "🤖 AI Basket Analysis" with confidence score
```

## Setup & Run

### Step 1: Start Infrastructure

```bash
# Start Redpanda (Kafka)
docker compose up -d redpanda redpanda-console redpanda-init-topics

# Wait for it to be healthy
docker ps  # Check status
```

### Step 2: Start Quarkus API + Frontend

```bash
# This starts both the backend API and serves the frontend
./start-kartshoppe.sh
```

**Expected output:**
- Quarkus API running on `http://localhost:8080`
- Frontend accessible at `http://localhost:8080`
- WebSocket endpoint: `ws://localhost:8080/ecommerce/{sessionId}/{userId}`

### Step 3: Start Flink Basket Analysis Job

```bash
# In a new terminal
./start-basket-job.sh
```

**Expected output:**
```
🛒 Starting Basket Analysis & Recommendation Job
📊 Parallelism: 2
💾 Checkpoint interval: 30000ms
🔧 Kafka: localhost:19092

📥 Creating Shopping Events Stream
   Topics: ecommerce-events, shopping-cart-events

🔧 PATTERNS: Session Windows + Broadcast State + CEP + Keyed State + Timers
   - Key by sessionId for basket tracking
   - Track active baskets with keyed state
   - Detect timeouts with timers (30 min)

⛏️  Mining Association Rules
   - Generate item-to-item recommendations
   - Calculate confidence, support, lift

📡 PATTERN 02: Broadcast State for Model Distribution
   - Broadcast learned patterns to all tasks
   - Enable real-time recommendations

✅ All patterns configured successfully!
```

## Testing the Flow

### Scenario 1: First Purchase - Pattern Learning

1. **Open Frontend:** `http://localhost:8080`
2. **Browse Products:** Click on "Laptops" category
3. **Add to Cart:**
   - Add "Gaming Laptop" (product ID: `prod_laptop_001`)
   - Add "Wireless Mouse" (product ID: `prod_mouse_001`)
   - Add "Keyboard" (product ID: `prod_keyboard_001`)
4. **Complete Purchase:** Go to cart → Checkout

**What Happens:**
- Frontend sends `ADD_TO_CART` events → Kafka
- Flink `BasketTrackerFunction` tracks items in keyed state
- On `ORDER_PLACED`, emits `BasketCompletion` event
- `PatternMinerFunction` mines patterns:
  ```
  laptop → mouse (confidence: 1.0, support: 1.0)
  laptop → keyboard (confidence: 1.0, support: 1.0)
  mouse → keyboard (confidence: 1.0, support: 1.0)
  ```
- Patterns broadcast to all tasks
- No recommendations yet (need more data)

**Check Flink Logs:**
```bash
# Look for:
✓ Basket completed: session=sess_xxx, items=3, value=1500.00
⛏️  Mined pattern: [laptop] → mouse (conf=1.00, sup=1.00, lift=1.00)
📡 Updated broadcast pattern: laptop_mouse
```

### Scenario 2: Second User - Recommendations!

1. **Open Incognito Window:** `http://localhost:8080`
   - This creates a new session/user
2. **Add Similar Item:**
   - Add "Gaming Laptop" to cart
3. **Watch for Recommendations:**
   - RecommendationsSection should appear
   - Shows: "🤖 AI Basket Analysis"
   - Badge: "Flink Pattern Mining"
   - Displays: Mouse, Keyboard (based on learned patterns)
   - Confidence: 75-100%

**What Happens:**
- Frontend sends `ADD_TO_CART` (laptop) → Kafka
- `RecommendationGeneratorFunction` receives event
- Checks broadcast state for patterns matching `[laptop]`
- Finds: `laptop → mouse`, `laptop → keyboard`
- Generates `RecommendationEvent`:
  ```json
  {
    "sessionId": "sess_456",
    "userId": "user_789",
    "recommendationType": "BASKET_BASED",
    "recommendedProducts": ["prod_mouse_001", "prod_keyboard_001"],
    "confidence": 0.85,
    "triggerEvent": "ADD_TO_CART",
    "context": {"basketSize": "1"}
  }
  ```
- Publishes to `product-recommendations` topic
- `RecommendationConsumer` in Quarkus receives it
- Forwards via WebSocket to frontend
- Frontend displays with special Flink badge

**Check Browser Console:**
```javascript
🎯 Received Flink basket recommendation: {
  recommendationType: "BASKET_BASED",
  recommendedProducts: ["prod_mouse_001", "prod_keyboard_001"],
  confidence: 0.85,
  source: "FLINK_BASKET_ANALYSIS"
}
```

### Scenario 3: View-Based Recommendations

1. **Just Browse:** Don't add to cart
2. **View Product:** Click on "Gaming Laptop"
3. **Watch for Recommendations:**
   - Type: "VIEW_BASED"
   - Shows related products viewed by others

**What Happens:**
- Frontend sends `PRODUCT_VIEW` event
- `RecommendationGeneratorFunction` generates view-based recommendations
- Uses patterns where laptop is in antecedents

## Monitoring

### Kafka Topics

```bash
# Watch recommendations being generated
docker exec -it redpanda rpk topic consume product-recommendations

# Watch basket patterns
docker exec -it redpanda rpk topic consume basket-patterns

# Watch raw events
docker exec -it redpanda rpk topic consume ecommerce-events
```

### Flink Job Output

```bash
# Tail the Flink job logs
# (automatically shown when running ./start-basket-job.sh)

# Look for these patterns:
Added prod_laptop_001 to basket sess_123 (total items: 1)
Basket completed: session=sess_123, items=3, value=1500.00
Mined pattern: [laptop] → mouse (conf=1.00, sup=1.00, lift=1.00)
Generated basket-based recommendations for sess_456: [mouse, keyboard]
```

### Quarkus Logs

```bash
# Look for:
📦 Received recommendation from Flink: {"sessionId":"sess_456"...}
✅ Routed recommendation to session=sess_456, user=user_789, type=BASKET_BASED, confidence=0.85
📨 Broadcasting Flink recommendation: session=sess_456, user=user_789
```

## Pattern Metrics

The recommendations show real metrics from pattern mining:

- **Confidence:** P(consequent | antecedents)
  - Example: 85% of users who bought laptop also bought mouse
- **Support:** P(antecedents AND consequent)
  - Example: 10% of all baskets contain both
- **Lift:** confidence / P(consequent)
  - Lift > 1: positive correlation
  - Example: 1.5x more likely to buy together

## Troubleshooting

### No Recommendations Appear

**Check:**
1. Is Flink job running? `ps aux | grep flink-recommendations`
2. Are topics created? `docker exec redpanda rpk topic list`
3. Is WebSocket connected? Check browser console for "WebSocket connected"
4. Any errors in Quarkus logs? `docker logs quarkus-api -f`

**Fix:**
```bash
# Restart everything
./stop-all.sh
./start-kartshoppe.sh
./start-basket-job.sh
```

### Recommendations Don't Update

**Reason:** Need to train the model first (Scenario 1)

**Fix:** Complete at least one purchase to create patterns

### Wrong Recommendations

**Reason:** Pattern mining needs more data

**Fix:** Complete 3-5 purchases to build better patterns

## Advanced: Pattern Parameters

Edit `PatternMinerFunction.java`:

```java
// Adjust thresholds
private static final double MIN_CONFIDENCE_FULL = 0.3;  // Lower = more recommendations
private static final double MIN_CONFIDENCE_PAIR = 0.4;  // Lower = more pair recommendations
private static final double MIN_SUPPORT = 0.01;         // Lower = allow rare patterns
```

## Files Modified

✅ **Backend (Quarkus):**
- `quarkus-api/.../RecommendationConsumer.java` (NEW)
- `quarkus-api/.../EcommerceWebsocket.java` (MODIFIED)

✅ **Frontend (React):**
- `kartshoppe-frontend/src/contexts/WebSocketContext.tsx` (MODIFIED)
- `kartshoppe-frontend/src/components/RecommendationsSection.tsx` (MODIFIED)

✅ **Flink:**
- `flink-recommendations/build.gradle` (MODIFIED)
- `flink-recommendations/.../BasketAnalysisJobRefactored.java`

✅ **Infrastructure:**
- `docker-compose.yml` (MODIFIED - added topics)
- `start-basket-job.sh` (NEW)

## Next Steps

1. **Add more patterns:** Try different product combinations
2. **Tune parameters:** Adjust confidence/support thresholds
3. **Add CEP:** Implement cart abandonment detection (see `flink-recommendations/patterns/03_cep/`)
4. **Use pre-training:** Try `BasketAnalysisJobWithPretraining` for better cold-start performance
5. **A/B testing:** Compare Flink recommendations vs. baseline
