# 🪟 FLINK PATTERN: Session Windows

> **Learning Pattern 01**: Group events by natural user sessions based on inactivity gaps

---

## 📚 Learning Objectives

After completing this module, you will be able to:

1. ✅ **Understand session windows** - How they differ from tumbling/sliding windows
2. ✅ **Configure session gaps** - Choose appropriate timeout values for your use case
3. ✅ **Process session data** - Aggregate all events within a session
4. ✅ **Handle dynamic windows** - Work with windows of varying duration
5. ✅ **Apply to real scenarios** - Shopping sessions, user journeys, device bursts

---

## 🎯 What Are Session Windows?

**Session windows** group events into **dynamic-length windows** separated by **gaps of inactivity**.

### Key Characteristics

| Feature | Description |
|---------|-------------|
| **Dynamic Duration** | Each window can be different length |
| **Gap-Based** | Windows close after N minutes of no activity |
| **Automatic Merging** | Overlapping sessions merge into one |
| **Event-Driven** | Windows grow as events arrive |
| **Natural Boundaries** | Follows actual user behavior patterns |

### Visual Representation

```
TIME ─────────────────────────────────────────────────────────▶

USER SESSION 1 (20 minutes):
  │
  ├─ 10:00:00  VIEW laptop
  ├─ 10:05:00  VIEW mouse
  ├─ 10:10:00  ADD laptop to cart
  ├─ 10:12:00  VIEW monitor
  │
  └─ 10:20:00  [No activity for 30 min]

     ┌─────────────────────────────┐
     │   SESSION 1 CLOSES          │
     │   Duration: 20 minutes      │
     │   Events: 4                 │
     │   Type: ABANDONED_CART      │
     └─────────────────────────────┘

     [30-minute gap]

USER SESSION 2 (5 minutes):
  │
  ├─ 11:00:00  VIEW keyboard
  ├─ 11:02:00  ADD keyboard
  ├─ 11:05:00  PURCHASE
  │
  └─ 11:05:00  [No activity for 30 min]

     ┌─────────────────────────────┐
     │   SESSION 2 CLOSES          │
     │   Duration: 5 minutes       │
     │   Events: 3                 │
     │   Type: PURCHASE            │
     └─────────────────────────────┘
```

---

## 🔄 Window Types Comparison

### Session vs Other Windows

```
TUMBLING WINDOWS (Fixed size, no overlap):
├──────┤├──────┤├──────┤├──────┤
0min   5min   10min  15min  20min

Example: Hourly sales reports


SLIDING WINDOWS (Fixed size, overlaps):
├──────┤
  ├──────┤
    ├──────┤
      ├──────┤
0min   5min   10min  15min  20min

Example: Rolling 5-minute average


SESSION WINDOWS (Dynamic size, gap-based):
├────────────┤        ├──┤  ├─────────┤
0min        12min   18min 20min     28min
  Session 1    Gap   S2 Gap Session 3

Example: User shopping sessions ← THIS MODULE!


GLOBAL WINDOWS (One window for all time):
├────────────────────────────────────────▶
0min                                    ∞

Example: With custom triggers for special logic
```

### When to Use Each

| Window Type | Use When | Example |
|-------------|----------|---------|
| **Tumbling** | Fixed-time batches | Hourly reports, daily summaries |
| **Sliding** | Rolling averages | 5-min moving avg, trend detection |
| **Session** | Natural activity periods | Shopping sessions, device bursts |
| **Global** | Custom trigger logic | Complex business rules |

---

## 🛍️ Real-World Example: E-commerce Sessions

### Scenario

An online store wants to analyze shopping behavior:
- Track what users view/add/purchase in one session
- Identify abandoned carts
- Understand browsing vs buying behavior

### Session Gap Choice

**30 minutes** - A good default for e-commerce:
- Short enough: Captures focused shopping
- Long enough: Allows browsing breaks
- Industry standard: Most sites use 20-40 min

### Session Types

```java
// From SessionWindowExample.java:

if (hasPurchase) {
    session.sessionType = "PURCHASE";          // 🎯 Successful conversion
} else if (addToCartCount > 0) {
    session.sessionType = "ABANDONED_CART";    // 🛒 Needs follow-up
} else if (viewCount >= 3) {
    session.sessionType = "BROWSER";           // 👀 Just looking
} else {
    session.sessionType = "QUICK_VISIT";       // ⚡ Bounced
}
```

---

## 💻 Code Walkthrough

### Step 1: Define Watermark Strategy

```java
WatermarkStrategy<EcommerceEvent> watermarkStrategy = WatermarkStrategy
    .<EcommerceEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5))  // Allow 5s late data
    .withTimestampAssigner(new SerializableTimestampAssigner<EcommerceEvent>() {
        @Override
        public long extractTimestamp(EcommerceEvent event, long recordTimestamp) {
            return event.timestamp;  // Use event's timestamp field
        }
    });
```

**Why watermarks?**
- Session windows use **event time** (when event occurred)
- Not processing time (when Flink received it)
- Watermarks tell Flink "all events before time T have arrived"

### Step 2: Apply Session Window

```java
DataStream<ShoppingSession> sessions = shoppingEvents
    .assignTimestampsAndWatermarks(watermarkStrategy)  // Enable event time
    .keyBy(event -> event.sessionId)                   // Partition by session ID
    .window(EventTimeSessionWindows.withGap(Time.minutes(30)))  // 30-min gap
    .process(new SessionAggregator())                  // Aggregate session data
    .name("Session Window Aggregation");
```

**Key points:**
- `keyBy(sessionId)`: Each user gets their own session tracking
- `withGap(Time.minutes(30))`: 30 minutes of no events = session ends
- `process(...)`: Called once per session with ALL events

### Step 3: Process Session Data

```java
public static class SessionAggregator
        extends ProcessWindowFunction<EcommerceEvent, ShoppingSession, String, TimeWindow> {

    @Override
    public void process(
            String sessionId,              // Key (session ID)
            Context context,               // Window metadata
            Iterable<EcommerceEvent> events,  // ALL events in this session
            Collector<ShoppingSession> out) { // Output collector

        TimeWindow window = context.window();

        ShoppingSession session = new ShoppingSession();
        session.sessionId = sessionId;
        session.sessionStart = window.getStart();   // First event time
        session.sessionEnd = window.getEnd();       // Last event time + gap
        session.sessionDuration = (window.getEnd() - window.getStart()) / 1000; // seconds

        // Aggregate all events
        Set<String> uniqueProducts = new HashSet<>();
        int viewCount = 0;
        int addCount = 0;
        boolean hasPurchase = false;

        for (EcommerceEvent event : events) {
            uniqueProducts.add(event.productId);

            switch (event.eventType) {
                case "VIEW_PRODUCT":
                    viewCount++;
                    break;
                case "ADD_TO_CART":
                    addCount++;
                    break;
                case "PURCHASE":
                case "CHECKOUT":
                    hasPurchase = true;
                    break;
            }
        }

        session.totalEvents = viewCount + addCount + (hasPurchase ? 1 : 0);
        session.uniqueProducts = uniqueProducts.size();

        // Classify session type
        if (hasPurchase) {
            session.sessionType = "PURCHASE";
        } else if (addCount > 0) {
            session.sessionType = "ABANDONED_CART";
        } else if (viewCount >= 3) {
            session.sessionType = "BROWSER";
        } else {
            session.sessionType = "QUICK_VISIT";
        }

        out.collect(session);  // Output one session summary
    }
}
```

**Important:** `process()` is called **once per session** with **all events** buffered.

---

## 🎓 Hands-On Exercises

### Exercise 1: Change Session Gap

**Task:** Modify the session gap from 30 minutes to 10 minutes.

**Steps:**
1. Find the constant in `SessionWindowExample.java`:
   ```java
   private static final Duration SESSION_GAP = Duration.ofMinutes(30);
   ```

2. Change to:
   ```java
   private static final Duration SESSION_GAP = Duration.ofMinutes(10);
   ```

3. Rebuild and run

**Expected Result:**
- More sessions (shorter timeout = more sessions close)
- Shorter average session duration
- Same events, but split differently

**Question:** How would this affect cart abandonment detection?

<details>
<summary>💡 Click to see answer</summary>

**Answer:**
- **More false positives**: Users taking a coffee break (11-15 min) would be marked as "abandoned" even if they come back
- **Better for mobile**: Mobile users have shorter attention spans - 10 min might be better
- **Worse for desktop**: Desktop users often browse longer - 30 min captures full sessions

**Best practice**: A/B test different gaps based on:
- Device type (mobile: 10-15 min, desktop: 20-30 min)
- Product category (fashion: longer, electronics: shorter)
- Historical data analysis
</details>

---

### Exercise 2: Add Session Metrics

**Task:** Calculate **average items per session** for each session type.

**Hints:**
- Track total items (viewCount + addToCartCount)
- Add field to `ShoppingSession` class
- Calculate in `SessionAggregator.process()`

**Solution:**

```java
// In ShoppingSession class:
public int totalItemsInteracted;

// In SessionAggregator.process():
session.totalItemsInteracted = viewCount + addCount;

// After process() completes, calculate averages:
sessions
    .keyBy(session -> session.sessionType)  // Group by type
    .process(new ProcessFunction<ShoppingSession, SessionMetrics>() {

        private transient ValueState<Integer> totalItemsState;
        private transient ValueState<Integer> sessionCountState;

        @Override
        public void processElement(
                ShoppingSession session,
                Context ctx,
                Collector<SessionMetrics> out) throws Exception {

            int totalItems = totalItemsState.value() == null ? 0 : totalItemsState.value();
            int count = sessionCountState.value() == null ? 0 : sessionCountState.value();

            totalItems += session.totalItemsInteracted;
            count += 1;

            totalItemsState.update(totalItems);
            sessionCountState.update(count);

            double avgItemsPerSession = (double) totalItems / count;

            SessionMetrics metrics = new SessionMetrics();
            metrics.sessionType = session.sessionType;
            metrics.avgItemsPerSession = avgItemsPerSession;
            metrics.sessionCount = count;

            out.collect(metrics);
        }
    });
```

**Expected Output:**
```
PURCHASE sessions: avg 4.2 items (high engagement)
ABANDONED_CART sessions: avg 3.1 items (moderate)
BROWSER sessions: avg 5.8 items (exploring, not buying)
QUICK_VISIT sessions: avg 1.2 items (bounced)
```

**Insight:** Browsers view MORE items but don't buy - potential for better product recommendations!

---

### Exercise 3: Detect Cart Abandonment Patterns

**Task:** Emit a side output for abandoned carts worth over $100.

**Requirements:**
1. Add `totalCartValue` field to track cart total
2. Use side outputs to route high-value abandoned carts
3. Output different stream for follow-up marketing

**Solution:**

```java
// Step 1: Define side output tag
public static final OutputTag<AbandonedCartAlert> HIGH_VALUE_ABANDONED =
    new OutputTag<AbandonedCartAlert>("high-value-abandoned") {};

// Step 2: Modify SessionAggregator
public static class SessionAggregator
        extends ProcessWindowFunction<EcommerceEvent, ShoppingSession, String, TimeWindow> {

    @Override
    public void process(
            String sessionId,
            Context context,
            Iterable<EcommerceEvent> events,
            Collector<ShoppingSession> out) throws Exception {

        // ... existing aggregation code ...

        double totalCartValue = 0.0;
        List<String> cartItems = new ArrayList<>();

        for (EcommerceEvent event : events) {
            if (event.eventType.equals("ADD_TO_CART")) {
                totalCartValue += event.productPrice;  // Assuming event has price
                cartItems.add(event.productId);
            }
        }

        session.totalCartValue = totalCartValue;

        // Classify session
        if (hasPurchase) {
            session.sessionType = "PURCHASE";
        } else if (addCount > 0) {
            session.sessionType = "ABANDONED_CART";

            // SIDE OUTPUT: High-value abandoned cart
            if (totalCartValue >= 100.0) {
                AbandonedCartAlert alert = new AbandonedCartAlert();
                alert.sessionId = sessionId;
                alert.userId = events.iterator().next().userId;  // Get user ID
                alert.cartValue = totalCartValue;
                alert.itemsInCart = cartItems;
                alert.sessionDuration = session.sessionDuration;

                context.output(HIGH_VALUE_ABANDONED, alert);

                LOG.warn("💰 High-value cart abandoned: ${} (Session: {})",
                    totalCartValue, sessionId);
            }
        }
        // ... rest of classification ...

        out.collect(session);
    }
}

// Step 3: Extract side output in main()
public static void main(String[] args) throws Exception {
    // ... create sessions stream ...

    SingleOutputStreamOperator<ShoppingSession> sessions = shoppingEvents
        .assignTimestampsAndWatermarks(watermarkStrategy)
        .keyBy(event -> event.sessionId)
        .window(EventTimeSessionWindows.withGap(Time.minutes(30)))
        .process(new SessionAggregator());

    // Get high-value abandoned carts
    DataStream<AbandonedCartAlert> highValueAbandoned =
        sessions.getSideOutput(HIGH_VALUE_ABANDONED);

    // Send to marketing system
    highValueAbandoned.print();  // Or sink to Kafka for email campaigns

    env.execute("Session Window with Cart Abandonment Detection");
}
```

**Use Case:**
- Automatically trigger email: "You left $150 worth of items in your cart! Here's 10% off..."
- Prioritize high-value carts for retargeting ads
- Analyze why users abandon expensive carts

---

## ⚠️ Common Pitfalls

### 1. **Choosing Wrong Gap Duration**

❌ **Problem:**
```java
// Gap too short (1 minute)
EventTimeSessionWindows.withGap(Time.minutes(1))
```
**Result:** User browsing between pages takes 2 minutes → Split into 2 sessions (wrong!)

✅ **Solution:**
```java
// Analyze your data first!
// E-commerce: 20-40 min
// Mobile games: 5-10 min
// IoT sensors: 1-5 min

EventTimeSessionWindows.withGap(Time.minutes(30))  // Based on domain
```

**How to choose:**
1. Analyze historical data: distribution of time between events
2. Look at industry standards for your domain
3. A/B test different values
4. Consider device type (mobile vs desktop)

---

### 2. **Forgetting Watermarks**

❌ **Problem:**
```java
// No watermark strategy!
DataStream<ShoppingSession> sessions = shoppingEvents
    .keyBy(event -> event.sessionId)
    .window(EventTimeSessionWindows.withGap(Time.minutes(30)))  // Won't work!
    .process(new SessionAggregator());
```
**Result:** Windows never close! Data buffers indefinitely.

✅ **Solution:**
```java
// Always assign watermarks for event time windows
DataStream<ShoppingSession> sessions = shoppingEvents
    .assignTimestampsAndWatermarks(WatermarkStrategy
        .<EcommerceEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5))
        .withTimestampAssigner((event, ts) -> event.timestamp))
    .keyBy(event -> event.sessionId)
    .window(EventTimeSessionWindows.withGap(Time.minutes(30)))
    .process(new SessionAggregator());
```

---

### 3. **Using ProcessWindowFunction for Everything**

❌ **Problem:**
```java
// Buffering all events in state (memory intensive!)
.window(EventTimeSessionWindows.withGap(Time.minutes(30)))
.process(new ProcessWindowFunction<Event, Result, String, TimeWindow>() {
    // Flink buffers ALL events until window closes
});
```

✅ **Better (if possible):**
```java
// Use AggregateFunction for incremental aggregation
.window(EventTimeSessionWindows.withGap(Time.minutes(30)))
.aggregate(new AverageAggregateFunction());  // Incremental - less memory!

// Or combine both:
.window(EventTimeSessionWindows.withGap(Time.minutes(30)))
.aggregate(
    new AverageAggregateFunction(),      // Incremental aggregation
    new AddWindowInfoProcessFunction()   // Access window metadata
);
```

**When to use which:**
- **ProcessWindowFunction**: Need ALL events (e.g., median, percentiles, complex logic)
- **AggregateFunction**: Simple aggregations (sum, avg, count) - more efficient
- **Both combined**: Best of both worlds

---

### 4. **Not Handling Late Data**

❌ **Problem:**
```java
// Default: late data is dropped silently
WatermarkStrategy.<Event>forBoundedOutOfOrderness(Duration.ofSeconds(5))
```
**Result:** Events arriving >5 seconds late are silently dropped - data loss!

✅ **Solution:**
```java
// Option 1: Increase allowed lateness
.window(EventTimeSessionWindows.withGap(Time.minutes(30)))
.allowedLateness(Time.minutes(5))  // Process late data up to 5 min late
.process(new SessionAggregator());

// Option 2: Side output for late data
OutputTag<Event> lateDataTag = new OutputTag<Event>("late-data") {};

SingleOutputStreamOperator<Session> sessions = events
    .window(EventTimeSessionWindows.withGap(Time.minutes(30)))
    .sideOutputLateData(lateDataTag)  // Capture late data
    .process(new SessionAggregator());

DataStream<Event> lateData = sessions.getSideOutput(lateDataTag);
lateData.print();  // Log or reprocess
```

---

### 5. **Session Merging Confusion**

❌ **Misunderstanding:**
"Each event creates a new session"

✅ **Reality:**
Sessions **merge automatically** if events overlap!

**Example:**
```
User A sends events:
  10:00  Event 1  →  Session A1 [10:00 - 10:30]
  10:15  Event 2  →  Extends A1 [10:00 - 10:45]
  10:40  Event 3  →  Extends A1 [10:00 - 11:10]

Result: ONE session with 3 events (not 3 sessions!)
```

**When merging happens:**
- New event arrives within gap of existing session
- Sessions automatically extend
- All events processed together in `process()`

---

## 🚀 Performance Tips

### 1. **Choose Right Parallelism**

```java
env.setParallelism(4);  // Match number of CPU cores

sessions
    .keyBy(event -> event.sessionId)  // Good: Distributes by session ID
    // NOT .keyBy(event -> event.userId)  // Bad if one user = many sessions
```

**Why:**
- Each parallel task handles a subset of session IDs
- More parallelism = better throughput (up to CPU limit)
- Over-parallelizing wastes resources

---

### 2. **Use Incremental Aggregation**

❌ **Inefficient:**
```java
.process(new ProcessWindowFunction<Event, Result, String, TimeWindow>() {
    public void process(..., Iterable<Event> events, ...) {
        // Flink buffers ALL events in memory
        for (Event e : events) {
            sum += e.value;  // Computed at window close
        }
    }
});
```

✅ **Efficient:**
```java
.aggregate(new AggregateFunction<Event, SumAccumulator, Double>() {
    public SumAccumulator add(Event event, SumAccumulator acc) {
        acc.sum += event.value;  // Incremental - computed as events arrive
        return acc;
    }
});
```

**Memory savings:**
- ProcessWindowFunction: O(n) memory (n = events in window)
- AggregateFunction: O(1) memory (fixed accumulator size)

---

### 3. **Configure State Backend**

```java
// Use RocksDB for large state (sessions with many events)
env.setStateBackend(new EmbeddedRocksDBStateBackend());
env.getCheckpointConfig().setCheckpointStorage("file:///path/to/checkpoints");
```

**Why:**
- Default (heap) state backend: Fast but limited by JVM memory
- RocksDB: Slower but handles TBs of state (spills to disk)

**When to use RocksDB:**
- Sessions with 1000s of events
- Many concurrent sessions (millions)
- Running in production with limited memory

---

### 4. **Tune Watermark Lateness**

```java
// Too strict (data loss):
WatermarkStrategy.forBoundedOutOfOrderness(Duration.ofSeconds(1))

// Too lenient (windows close late):
WatermarkStrategy.forBoundedOutOfOrderness(Duration.ofMinutes(10))

// Just right (analyze your data!):
WatermarkStrategy.forBoundedOutOfOrderness(Duration.ofSeconds(5))
```

**Trade-off:**
- Lower lateness: Faster window close, but more dropped late events
- Higher lateness: Fewer dropped events, but slower results

**How to tune:**
1. Monitor late data metrics
2. Check event timestamp vs arrival time distribution
3. Balance latency requirements vs completeness

---

## 🌍 Real-World Applications

### 1. **E-commerce (This Example!)**

**Use Case:** Shopping session analysis

**Implementation:**
- Gap: 30 minutes (industry standard)
- Metrics: Cart abandonment rate, browse-to-buy ratio, avg session value
- Actions: Trigger remarketing, personalized recommendations

**Example Alert:**
```
🛒 Cart Abandoned: Session xyz-123
   Value: $234.56
   Items: Laptop ($199), Mouse ($24.99), Keyboard ($10.57)
   Duration: 18 minutes
   Last activity: 2025-10-06 14:32:15

   Action: Send email with 10% discount code
```

---

### 2. **IoT Device Monitoring**

**Use Case:** Detect device activity bursts

**Implementation:**
```java
DataStream<DeviceSession> deviceSessions = sensorReadings
    .assignTimestampsAndWatermarks(watermarkStrategy)
    .keyBy(reading -> reading.deviceId)
    .window(EventTimeSessionWindows.withGap(Time.minutes(5)))  // 5-min gap for IoT
    .aggregate(new DeviceActivityAggregator());
```

**Scenario:**
```
Smart thermostat sends temperature readings:
  14:00:00  72°F
  14:00:30  72°F
  14:01:00  73°F
  ... [5-minute gap] ...
  14:10:00  New session starts

Session 1: Duration 1 min, Avg temp 72.3°F, Variance low → Normal
Session 2: Duration 20 min, Avg temp 85°F, Variance high → ALERT: Overheating!
```

---

### 3. **Mobile Gaming**

**Use Case:** Track play sessions

**Implementation:**
```java
DataStream<GameSession> gameSessions = gameEvents
    .assignTimestampsAndWatermarks(watermarkStrategy)
    .keyBy(event -> event.playerId)
    .window(EventTimeSessionWindows.withGap(Time.minutes(10)))  // Shorter gap for mobile
    .process(new GameSessionAnalyzer());

// Metrics tracked:
// - Session duration
// - Levels completed
// - In-app purchases
// - Churn risk (short sessions = churning?)
```

**Business Value:**
- Identify disengaged players (short sessions)
- Reward long sessions (send in-game gifts)
- A/B test features (session duration before/after)

---

### 4. **Log Analysis**

**Use Case:** Group user actions for debugging

**Implementation:**
```java
DataStream<UserActionSession> debugSessions = appLogs
    .filter(log -> log.level.equals("ERROR"))
    .keyBy(log -> log.userId)
    .window(EventTimeSessionWindows.withGap(Time.minutes(2)))  // 2-min gap for logs
    .process(new ErrorSessionAnalyzer());

// Output: All errors user experienced in one session
// Helps debug: "User xyz had 5 errors in 3 minutes - what happened?"
```

---

### 5. **Network Packet Flows**

**Use Case:** Group packets into TCP flows

**Implementation:**
```java
DataStream<PacketFlow> flows = packets
    .keyBy(packet -> packet.flowId)  // 5-tuple: src, dst, src_port, dst_port, protocol
    .window(EventTimeSessionWindows.withGap(Time.seconds(30)))  // 30s timeout
    .aggregate(new FlowAggregator());

// Detect:
// - Abnormal flows (too many packets)
// - Port scans (many flows from one IP)
// - DDoS attacks (huge session sizes)
```

---

## 📊 Metrics to Track

### Session-Level Metrics

```java
public class ShoppingSession {
    // Duration metrics
    public long sessionDuration;           // Total session length
    public long avgTimeBetweenEvents;      // User engagement level

    // Volume metrics
    public int totalEvents;                // Activity level
    public int uniqueProducts;             // Breadth of interest
    public int viewCount;                  // Browsing behavior
    public int addToCartCount;             // Purchase intent

    // Business metrics
    public double totalCartValue;          // Revenue potential
    public boolean completedPurchase;      // Conversion
    public String sessionType;             // Classification

    // Context
    public String deviceType;              // Mobile vs Desktop
    public String referrerSource;          // How they arrived
}
```

### Aggregated Metrics

```java
// Track across all sessions:
sessions
    .keyBy(session -> session.sessionType)
    .process(new ProcessFunction<ShoppingSession, SessionStats>() {

        private transient ValueState<Long> countState;
        private transient ValueState<Double> avgDurationState;
        private transient ValueState<Double> conversionRateState;

        @Override
        public void processElement(ShoppingSession session, ...) {
            // Update running averages:
            // - Avg session duration by type
            // - Conversion rate (PURCHASE / total sessions)
            // - Avg cart value
            // - Items per session
        }
    });
```

**Example Dashboard:**
```
SESSION ANALYTICS (Last 24 hours)
=====================================
PURCHASE Sessions:        1,234 (15.2% of total)
  Avg Duration:           8.3 minutes
  Avg Cart Value:         $127.45
  Avg Items:              4.2

ABANDONED_CART Sessions:  3,456 (42.5% of total)
  Avg Duration:           12.1 minutes
  Avg Cart Value:         $89.23  ← Opportunity!
  Recovery Rate:          23%     ← Send more emails

BROWSER Sessions:         2,890 (35.6% of total)
  Avg Duration:           15.7 minutes
  Avg Items Viewed:       8.9    ← High engagement, low conversion

QUICK_VISIT Sessions:     567 (7.0% of total)
  Avg Duration:           1.2 minutes
  Bounce Rate:            87%    ← Improve landing pages
```

---

## 🧪 Testing Your Session Windows

### Unit Test Template

```java
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.EventTimeSessionWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.junit.Test;

public class SessionWindowTest {

    @Test
    public void testSessionAggregation() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);  // Deterministic for testing

        // Create test events
        List<EcommerceEvent> testEvents = Arrays.asList(
            createEvent("session-1", "user-1", "VIEW_PRODUCT", "LAPTOP", 1000L),
            createEvent("session-1", "user-1", "ADD_TO_CART", "LAPTOP", 2000L),
            createEvent("session-1", "user-1", "PURCHASE", "LAPTOP", 3000L)
            // No more events → 30-min gap → session closes
        );

        DataStream<EcommerceEvent> events = env.fromCollection(testEvents);

        // Apply session window
        DataStream<ShoppingSession> sessions = events
            .assignTimestampsAndWatermarks(WatermarkStrategy
                .<EcommerceEvent>forMonotonousTimestamps()
                .withTimestampAssigner((event, ts) -> event.timestamp))
            .keyBy(e -> e.sessionId)
            .window(EventTimeSessionWindows.withGap(Time.minutes(30)))
            .process(new SessionAggregator());

        // Collect results
        List<ShoppingSession> results = new ArrayList<>();
        sessions.executeAndCollect().forEachRemaining(results::add);

        // Assertions
        assertEquals(1, results.size());
        ShoppingSession session = results.get(0);
        assertEquals("PURCHASE", session.sessionType);
        assertEquals(3, session.totalEvents);
        assertEquals(1, session.viewCount);
        assertEquals(1, session.addToCartCount);
        assertTrue(session.completedPurchase);
    }
}
```

---

## ❓ Quiz Questions

Test your understanding!

### Question 1: Gap Duration

What happens if a user views a product at 10:00, then views another at 10:45 (with a 30-minute gap configured)?

A) Both events in one session
B) Two separate sessions
C) Second event is dropped
D) Session window errors

<details>
<summary>💡 Click to see answer</summary>

**Answer: B) Two separate sessions**

**Explanation:**
- First event at 10:00 creates session [10:00 - 10:30]
- 30 minutes of no activity → session closes at 10:30
- Second event at 10:45 creates new session [10:45 - 11:15]

**Time gap between events:** 45 minutes > 30-minute gap = separate sessions
</details>

---

### Question 2: Window Merging

User sends events at: 10:00, 10:15, 10:40 (with 30-min gap). How many sessions?

A) 1 session
B) 2 sessions
C) 3 sessions
D) Depends on watermark

<details>
<summary>💡 Click to see answer</summary>

**Answer: A) 1 session**

**Explanation:**
```
10:00  Event 1 → Session [10:00 - 10:30]
10:15  Event 2 → Extends session to [10:00 - 10:45] (within 30-min gap)
10:40  Event 3 → Extends session to [10:00 - 11:10] (within 30-min gap)

Final: ONE session [10:00 - 11:10] with all 3 events
```

Sessions **merge automatically** when events arrive within the gap!
</details>

---

### Question 3: ProcessWindowFunction

When is `process()` called in `ProcessWindowFunction`?

A) For each event as it arrives
B) Once when the window closes
C) Every time the watermark advances
D) When parallelism changes

<details>
<summary>💡 Click to see answer</summary>

**Answer: B) Once when the window closes**

**Explanation:**
- Flink buffers all events for the window
- When watermark passes window end + gap → window closes
- `process()` is called ONCE with ALL buffered events
- This is why it receives `Iterable<Event>` (all events in window)

**Memory implication:** All events buffered until window closes - use incremental aggregation if possible!
</details>

---

### Question 4: Event Time vs Processing Time

Session windows use event time. What does this mean?

A) Windows based on when Flink received the event
B) Windows based on when the event actually occurred
C) Windows based on system clock
D) Windows based on checkpoint time

<details>
<summary>💡 Click to see answer</summary>

**Answer: B) Windows based on when the event actually occurred**

**Explanation:**
- **Event time:** Timestamp in the event data (e.g., `event.timestamp`)
- **Processing time:** When Flink operator processes the event

**Why event time for sessions?**
- User actions happened at specific times (10:00, 10:05, etc.)
- Network delays shouldn't affect session grouping
- Reproducible results (reprocessing gives same sessions)

**Example:**
```
Event: User clicked "Add to Cart" at 10:00
  ↓
Network delay...
  ↓
Flink receives at 10:02
  ↓
Uses 10:00 (event time) for session window, not 10:02
```
</details>

---

### Question 5: Performance

Which is more memory-efficient for session windows?

A) `ProcessWindowFunction` for all aggregations
B) `AggregateFunction` for incremental aggregation
C) Both are the same
D) Depends on gap duration

<details>
<summary>💡 Click to see answer</summary>

**Answer: B) AggregateFunction for incremental aggregation**

**Explanation:**

**ProcessWindowFunction:**
```java
// Buffers ALL events in memory
.process(new ProcessWindowFunction<Event, Result, String, TimeWindow>() {
    public void process(..., Iterable<Event> events, ...) {
        for (Event e : events) sum += e.value;  // O(n) memory
    }
});
```

**AggregateFunction:**
```java
// Incremental - maintains only accumulator
.aggregate(new AggregateFunction<Event, SumAccumulator, Double>() {
    public SumAccumulator add(Event e, SumAccumulator acc) {
        acc.sum += e.value;  // O(1) memory
        return acc;
    }
});
```

**Memory:**
- ProcessWindowFunction: O(n) where n = events in session (could be 1000s!)
- AggregateFunction: O(1) fixed accumulator size

**When to use ProcessWindowFunction:**
- Need ALL events (e.g., calculate median, find specific patterns)
- Otherwise, prefer `AggregateFunction` or combine both
</details>

---

## 🎯 Summary

### Key Takeaways

✅ **Session windows** group events by natural activity periods (gap-based)

✅ **Dynamic duration** - each session can be different length (vs fixed tumbling/sliding)

✅ **Automatic merging** - overlapping sessions combine into one

✅ **Event time** - uses when events occurred, not when Flink received them

✅ **Watermarks required** - tell Flink when to close windows

✅ **Use cases:** Shopping sessions, device bursts, user journeys, log analysis

✅ **Performance:** Use incremental aggregation (`AggregateFunction`) when possible

---

### Next Steps

1. ✅ **Run the example:** `SessionWindowExample.java`
2. ✅ **Complete exercises:** Modify gap, add metrics, detect abandonment
3. 📚 **Learn more patterns:**
   - Pattern 02: Broadcast State (distribute ML models)
   - Pattern 03: CEP (complex event sequences)
4. 🔗 **Combine patterns:** See `BasketAnalysisJob.java` for pattern composition

---

## 📚 Further Reading

- [Flink Session Windows Docs](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/operators/windows/#session-windows)
- [Event Time and Watermarks](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/concepts/time/)
- [ProcessWindowFunction](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/operators/windows/#processwindowfunction)
- [Window Aggregations](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/operators/windows/#aggregatefunction)

---

<div align="center">
  <b>🎓 Pattern 01: Session Windows - Complete!</b><br>
  <i>Next: Pattern 02 - Broadcast State for ML Model Distribution</i>
</div>
