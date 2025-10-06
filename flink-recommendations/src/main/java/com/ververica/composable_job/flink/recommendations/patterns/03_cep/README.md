# 🔍 FLINK PATTERN: Complex Event Processing (CEP)

> **Learning Pattern 03**: Detect complex patterns in event sequences using declarative pattern matching

---

## 📚 Learning Objectives

After completing this module, you will be able to:

1. ✅ **Understand CEP** - Match sequential event patterns declaratively
2. ✅ **Define patterns** - Use pattern API (begin, next, followedBy, where)
3. ✅ **Apply quantifiers** - oneOrMore, times(n), optional, greedy
4. ✅ **Handle time constraints** - Patterns must complete within time windows
5. ✅ **Extract matches** - Process matched event sequences

---

## 🎯 What is Complex Event Processing (CEP)?

**CEP** detects **sequences of events** that match predefined patterns, enabling recognition of complex behaviors and anomalies.

### Key Characteristics

| Feature | Description |
|---------|-------------|
| **Sequential Matching** | Events must occur in specific order |
| **Declarative Syntax** | Define patterns, not manual state management |
| **Time Constraints** | Patterns must complete within time window |
| **Event Selection** | Filter events with predicates (where conditions) |
| **Quantifiers** | Specify how many times events occur |

### Visual Representation

```
EVENT STREAM:
┌──────┬──────┬──────┬──────┬──────┬──────┬──────┬──────┐
│ VIEW │ VIEW │ ADD  │ ADD  │ VIEW │ ... │ [30min gap] │
└──────┴──────┴──────┴──────┴──────┴──────┴──────┴──────┘
   ↓      ↓      ↓      ↓      ↓
   │      │      └──────┴─────────── PATTERN MATCHED!
   │      │                           "Cart Abandonment"
   │      │                           (ADD + no PURCHASE)
   └──────┴────────────────────────── Not part of pattern

CEP PATTERN DEFINITION:
Pattern: "Cart Abandonment"
  ┌─────────────────────────────────┐
  │ begin("add")                    │
  │   .where(type = ADD_TO_CART)   │
  │   .oneOrMore()                  │ ← Match 1+ ADD events
  │ .followedBy("no-purchase")      │
  │   .where(type != PURCHASE)      │ ← Followed by non-PURCHASE
  │   .within(Time.minutes(30))     │ ← Within 30 minutes
  └─────────────────────────────────┘

MATCHED PATTERN:
  ADD_TO_CART (laptop)
  ADD_TO_CART (mouse)
  [30 min timeout - no PURCHASE]
  → Emit CartAbandonmentAlert
```

---

## 🔄 CEP vs Other Patterns

### Comparison Table

| Pattern | Event Order | Time Handling | State Management | Use Case |
|---------|-------------|---------------|------------------|----------|
| **CEP** | Sequential (order matters) | Time windows | Automatic | Fraud detection, workflows |
| **Session Windows** | Unordered (gap-based) | Event time gaps | Automatic | User sessions |
| **ProcessFunction** | Any | Manual timers | Manual ValueState | Custom logic |
| **Joins** | Two streams | Time-based windows | Automatic | Stream enrichment |

### When to Use CEP

✅ **Use CEP when:**
- Event **order** matters (A must occur before B)
- Detecting **complex sequences** (A → B → C within 10 min)
- **Negative conditions** (event did NOT occur)
- **Fraud detection** (unusual transaction patterns)
- **Workflow monitoring** (process step tracking)

❌ **Don't use CEP when:**
- Event order doesn't matter (use session windows)
- Simple single-event filtering (use filter())
- No sequential dependencies (use ProcessFunction)
- Just aggregating events (use windows)

---

## 🛒 Real-World Example: Cart Abandonment

### Scenario

E-commerce site wants to detect cart abandonment:
- User adds items to cart
- User doesn't purchase within 30 minutes
- Send email: "You left items in your cart! Here's 10% off"

### Pattern Definition

```
DESIRED SEQUENCE:
  Step 1: ADD_TO_CART (one or more times)
  Step 2: NO PURCHASE (within 30 minutes)

ALERT ACTION:
  Send email with cart items and discount code
```

### Traditional Approach (Manual State) ❌

```java
// Manual state management - COMPLEX!
public class ManualCartAbandonmentDetector extends KeyedProcessFunction<...> {

    private ValueState<List<String>> cartItemsState;
    private ValueState<Long> lastAddTimeState;
    private ValueState<Long> timerState;

    @Override
    public void processElement(Event event, Context ctx, ...) {
        if (event.type.equals("ADD_TO_CART")) {
            // Add to cart state
            List<String> items = cartItemsState.value();
            items.add(event.productId);
            cartItemsState.update(items);

            // Delete old timer
            Long oldTimer = timerState.value();
            if (oldTimer != null) {
                ctx.timerService().deleteProcessingTimeTimer(oldTimer);
            }

            // Register new timer
            long newTimer = ctx.timerService().currentProcessingTime() + 30 * 60 * 1000;
            ctx.timerService().registerProcessingTimeTimer(newTimer);
            timerState.update(newTimer);
            lastAddTimeState.update(event.timestamp);

        } else if (event.type.equals("PURCHASE")) {
            // Clear cart state (purchased!)
            cartItemsState.clear();
            Long timer = timerState.value();
            if (timer != null) {
                ctx.timerService().deleteProcessingTimeTimer(timer);
            }
            timerState.clear();
        }
    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, ...) {
        // Timer fired - cart abandoned
        List<String> items = cartItemsState.value();
        if (items != null && !items.isEmpty()) {
            // Emit abandonment alert
            out.collect(new CartAbandonmentAlert(items));
        }
    }
}
```

**Problems:**
- 40+ lines of boilerplate
- Manual timer management
- Easy to get wrong (forget to delete timer, etc.)
- Hard to understand intent

### CEP Approach ✅

```java
// Declarative pattern - SIMPLE!
Pattern<Event, ?> cartAbandonmentPattern = Pattern
    .<Event>begin("add-to-cart")
    .where(event -> event.type.equals("ADD_TO_CART"))
    .oneOrMore()  // One or more items added
    .followedBy("no-purchase")
    .where(event -> !event.type.equals("PURCHASE"))
    .oneOrMore()
    .optional()
    .within(Time.minutes(30));  // Within 30 minutes

PatternStream<Event> matches = CEP.pattern(eventStream, cartAbandonmentPattern);

DataStream<CartAbandonmentAlert> alerts = matches.select(pattern -> {
    List<Event> cartEvents = pattern.get("add-to-cart");
    return new CartAbandonmentAlert(cartEvents);
});
```

**Benefits:**
- 10 lines (75% reduction!)
- Declarative (what, not how)
- Automatic timer management
- Easy to understand and modify

---

## 💻 Code Walkthrough

### Step 1: Define Pattern

```java
Pattern<EcommerceEvent, ?> cartAbandonmentPattern = Pattern
    .<EcommerceEvent>begin("add-to-cart")  // Start with ADD_TO_CART events
    .where(new SimpleCondition<EcommerceEvent>() {
        @Override
        public boolean filter(EcommerceEvent event) {
            return event.eventType.equals("ADD_TO_CART");
        }
    })
    .oneOrMore()  // Match one or more ADD_TO_CART events
    .followedBy("view-or-end")  // Followed by anything (relaxed contiguity)
    .where(new SimpleCondition<EcommerceEvent>() {
        @Override
        public boolean filter(EcommerceEvent event) {
            return !event.eventType.equals("PURCHASE");  // NOT a purchase
        }
    })
    .oneOrMore()
    .optional()  // May or may not occur
    .within(Time.minutes(30));  // All within 30 minutes
```

**Key components:**
- `begin("name")`: Start of pattern with name for extraction
- `where(condition)`: Filter events matching condition
- `oneOrMore()`: Quantifier (match 1 or more events)
- `followedBy("name")`: Relaxed contiguity (other events can be between)
- `within(time)`: Time constraint for entire pattern

---

### Step 2: Apply Pattern to Stream

```java
// Input stream must be keyed (patterns match per key)
DataStream<EcommerceEvent> keyedStream = events
    .assignTimestampsAndWatermarks(watermarkStrategy)
    .keyBy(event -> event.sessionId);  // Key by session ID

// Apply pattern
PatternStream<EcommerceEvent> patternStream = CEP.pattern(
    keyedStream,
    cartAbandonmentPattern
);
```

**Important:**
- Stream must be keyed (CEP matches patterns per key)
- Watermarks required for event time patterns
- Each key tracks pattern state independently

---

### Step 3: Extract Matched Patterns

```java
DataStream<CartAbandonmentAlert> alerts = patternStream.select(
    new PatternSelectFunction<EcommerceEvent, CartAbandonmentAlert>() {

        @Override
        public CartAbandonmentAlert select(Map<String, List<EcommerceEvent>> pattern) {
            // Get all ADD_TO_CART events
            List<EcommerceEvent> cartEvents = pattern.get("add-to-cart");

            CartAbandonmentAlert alert = new CartAbandonmentAlert();
            alert.sessionId = cartEvents.get(0).sessionId;
            alert.userId = cartEvents.get(0).userId;
            alert.itemsInCart = new ArrayList<>();
            alert.totalItems = cartEvents.size();

            // Collect all products added to cart
            for (EcommerceEvent event : cartEvents) {
                alert.itemsInCart.add(event.productId);
            }

            return alert;
        }
    }
);
```

**Pattern map structure:**
```
Map<String, List<EcommerceEvent>>
  ↓
  "add-to-cart" → [Event1, Event2, Event3]  ← All matched ADD_TO_CART events
  "view-or-end" → [Event4, Event5]          ← Subsequent non-PURCHASE events
```

---

## 🎓 Hands-On Exercises

### Exercise 1: Add Value Threshold

**Task:** Only trigger abandonment alert if cart value exceeds $100.

**Steps:**
1. Modify pattern to check total cart value
2. Use `where()` with stateful condition
3. Calculate total in pattern select function

**Solution:**

```java
// Option 1: Check in select function
DataStream<CartAbandonmentAlert> alerts = patternStream.select(
    new PatternSelectFunction<EcommerceEvent, CartAbandonmentAlert>() {

        @Override
        public CartAbandonmentAlert select(Map<String, List<EcommerceEvent>> pattern) {
            List<EcommerceEvent> cartEvents = pattern.get("add-to-cart");

            // Calculate total cart value
            double totalValue = 0.0;
            List<String> items = new ArrayList<>();

            for (EcommerceEvent event : cartEvents) {
                totalValue += event.productPrice;  // Assuming price field exists
                items.add(event.productId);
            }

            // Only emit alert if value > $100
            if (totalValue >= 100.0) {
                CartAbandonmentAlert alert = new CartAbandonmentAlert();
                alert.sessionId = cartEvents.get(0).sessionId;
                alert.userId = cartEvents.get(0).userId;
                alert.itemsInCart = items;
                alert.totalItems = cartEvents.size();
                alert.totalValue = totalValue;

                LOG.warn("High-value cart abandoned: ${}", totalValue);

                return alert;
            }

            return null;  // Filter out low-value carts
        }
    }
).filter(alert -> alert != null);  // Remove nulls

// Option 2: Use flatSelect for conditional emission
DataStream<CartAbandonmentAlert> alerts = patternStream.flatSelect(
    new PatternFlatSelectFunction<EcommerceEvent, CartAbandonmentAlert>() {

        @Override
        public void flatSelect(
                Map<String, List<EcommerceEvent>> pattern,
                Collector<CartAbandonmentAlert> out) {

            List<EcommerceEvent> cartEvents = pattern.get("add-to-cart");
            double totalValue = cartEvents.stream()
                .mapToDouble(e -> e.productPrice)
                .sum();

            if (totalValue >= 100.0) {
                CartAbandonmentAlert alert = new CartAbandonmentAlert();
                // ... populate alert ...
                out.collect(alert);  // Only emit if condition met
            }
            // Otherwise, emit nothing
        }
    }
);
```

**Use Case:**
- Focus recovery efforts on high-value carts
- Don't spam users with low-value cart reminders
- Prioritize sales team follow-ups

---

### Exercise 2: Multi-Pattern Detection

**Task:** Detect both cart abandonment AND quick purchases in same job.

**Requirements:**
1. Define two patterns (abandonment + quick purchase)
2. Apply both to same stream
3. Output to different sinks

**Solution:**

```java
// Pattern 1: Cart Abandonment (as before)
Pattern<EcommerceEvent, ?> abandonmentPattern = Pattern
    .<EcommerceEvent>begin("add")
    .where(e -> e.eventType.equals("ADD_TO_CART"))
    .oneOrMore()
    .followedBy("no-purchase")
    .where(e -> !e.eventType.equals("PURCHASE"))
    .oneOrMore().optional()
    .within(Time.minutes(30));

PatternStream<EcommerceEvent> abandonmentMatches = CEP.pattern(keyedStream, abandonmentPattern);
DataStream<CartAbandonmentAlert> abandonments = abandonmentMatches.select(...);

// Pattern 2: Quick Purchase (View → Add → Purchase < 10 min)
Pattern<EcommerceEvent, ?> quickPurchasePattern = Pattern
    .<EcommerceEvent>begin("view")
    .where(e -> e.eventType.equals("VIEW_PRODUCT"))
    .next("add")  // Strict: next event must be ADD
    .where(e -> e.eventType.equals("ADD_TO_CART"))
    .next("purchase")  // Strict: next event must be PURCHASE
    .where(e -> e.eventType.equals("PURCHASE"))
    .within(Time.minutes(10));

PatternStream<EcommerceEvent> quickPurchaseMatches = CEP.pattern(keyedStream, quickPurchasePattern);

DataStream<QuickPurchaseEvent> quickPurchases = quickPurchaseMatches.select(
    new PatternSelectFunction<EcommerceEvent, QuickPurchaseEvent>() {

        @Override
        public QuickPurchaseEvent select(Map<String, List<EcommerceEvent>> pattern) {
            EcommerceEvent viewEvent = pattern.get("view").get(0);
            EcommerceEvent purchaseEvent = pattern.get("purchase").get(0);

            QuickPurchaseEvent qp = new QuickPurchaseEvent();
            qp.userId = viewEvent.userId;
            qp.productId = purchaseEvent.productId;
            qp.timeToConversion = purchaseEvent.timestamp - viewEvent.timestamp;

            LOG.info("Quick purchase: {} in {} seconds",
                qp.productId, qp.timeToConversion / 1000);

            return qp;
        }
    }
);

// Output to different sinks
abandonments.sinkTo(abandonmentKafkaSink);  // For marketing team
quickPurchases.sinkTo(analyticsKafkaSink);  // For product analytics
```

**Use Case:**
- Abandonments → Send recovery emails
- Quick purchases → Learn what drives fast conversions
- Analyze: Why do some users buy quickly while others abandon?

---

### Exercise 3: Fraud Detection Pattern

**Task:** Detect potential fraud: Large purchase immediately after account creation.

**Requirements:**
1. Match: ACCOUNT_CREATED → PURCHASE > $500 within 5 minutes
2. Use `next()` for strict contiguity
3. Filter by purchase amount

**Solution:**

```java
// Fraud Pattern: New account → Large purchase
Pattern<UserEvent, ?> fraudPattern = Pattern
    .<UserEvent>begin("account-created")
    .where(new SimpleCondition<UserEvent>() {
        @Override
        public boolean filter(UserEvent event) {
            return event.eventType.equals("ACCOUNT_CREATED");
        }
    })
    .next("large-purchase")  // Strict: no events between
    .where(new SimpleCondition<UserEvent>() {
        @Override
        public boolean filter(UserEvent event) {
            return event.eventType.equals("PURCHASE") && event.amount > 500.0;
        }
    })
    .within(Time.minutes(5));  // Within 5 minutes

PatternStream<UserEvent> fraudMatches = CEP.pattern(
    userEvents.keyBy(e -> e.userId),
    fraudPattern
);

DataStream<FraudAlert> fraudAlerts = fraudMatches.select(
    new PatternSelectFunction<UserEvent, FraudAlert>() {

        @Override
        public FraudAlert select(Map<String, List<UserEvent>> pattern) {
            UserEvent created = pattern.get("account-created").get(0);
            UserEvent purchase = pattern.get("large-purchase").get(0);

            FraudAlert alert = new FraudAlert();
            alert.userId = created.userId;
            alert.accountCreatedTime = created.timestamp;
            alert.purchaseTime = purchase.timestamp;
            alert.purchaseAmount = purchase.amount;
            alert.timeDelta = purchase.timestamp - created.timestamp;
            alert.severity = "HIGH";
            alert.reason = String.format("$%.2f purchase %ds after account creation",
                purchase.amount, alert.timeDelta / 1000);

            LOG.error("🚨 FRAUD ALERT: {}", alert.reason);

            return alert;
        }
    }
);

// Send to fraud investigation queue
fraudAlerts.sinkTo(fraudInvestigationSink);
```

**Real-World Fraud Patterns:**
```
Pattern 1: Velocity Check
  - Multiple purchases from different locations within 1 hour

Pattern 2: Account Takeover
  - Password change → Large purchase → Address change

Pattern 3: Card Testing
  - 5+ small purchases ($1) followed by large purchase

Pattern 4: Return Fraud
  - Purchase → Immediate return → Repeat 3+ times
```

---

## ⚠️ Common Pitfalls

### 1. **Forgetting to Key Stream**

❌ **Problem:**
```java
// Not keyed!
DataStream<Event> events = env.addSource(...);

// Runtime error: CEP requires keyed stream
PatternStream<Event> matches = CEP.pattern(events, pattern);
```

✅ **Solution:**
```java
// Key stream before applying pattern
DataStream<Event> keyedEvents = events
    .keyBy(event -> event.userId);  // Or sessionId, orderId, etc.

PatternStream<Event> matches = CEP.pattern(keyedEvents, pattern);
```

**Why keying is required:**
- CEP tracks pattern state per key
- Different users/sessions have independent patterns
- Ensures correct pattern matching

---

### 2. **next() vs followedBy() Confusion**

❌ **Problem:**
```java
// Using next() but expecting events between
Pattern<Event, ?> pattern = Pattern
    .<Event>begin("view")
    .where(e -> e.type.equals("VIEW"))
    .next("purchase")  // ❌ Strict: expects immediate next event
    .where(e -> e.type.equals("PURCHASE"));

// Event sequence: VIEW, ADD_TO_CART, PURCHASE
// Pattern does NOT match! (ADD_TO_CART breaks strict contiguity)
```

✅ **Solution:**
```java
// Use followedBy() to allow events between
Pattern<Event, ?> pattern = Pattern
    .<Event>begin("view")
    .where(e -> e.type.equals("VIEW"))
    .followedBy("purchase")  // ✅ Relaxed: allows events between
    .where(e -> e.type.equals("PURCHASE"));

// Event sequence: VIEW, ADD_TO_CART, PURCHASE
// Pattern MATCHES! (ADD_TO_CART is ignored)
```

**Contiguity Types:**

| Method | Contiguity | Behavior | Example |
|--------|------------|----------|---------|
| `.next()` | Strict | Next event MUST match | A → B (no events between) |
| `.followedBy()` | Relaxed | Can have non-matching events between | A → ... → B |
| `.followedByAny()` | Non-deterministic | Can have matching events between | A → B → B (matches first or second B) |

---

### 3. **Not Setting Time Constraints**

❌ **Problem:**
```java
// No time constraint!
Pattern<Event, ?> pattern = Pattern
    .<Event>begin("add")
    .where(e -> e.type.equals("ADD_TO_CART"))
    .oneOrMore()
    .followedBy("purchase")
    .where(e -> e.type.equals("PURCHASE"));
    // Missing: .within(Time.minutes(30))

// Pattern state grows indefinitely → Memory leak!
```

✅ **Solution:**
```java
// Always add time constraint
Pattern<Event, ?> pattern = Pattern
    .<Event>begin("add")
    .where(e -> e.type.equals("ADD_TO_CART"))
    .oneOrMore()
    .followedBy("purchase")
    .where(e -> e.type.equals("PURCHASE"))
    .within(Time.minutes(30));  // ✅ Pattern expires after 30 min
```

**Why time constraints matter:**
- Without timeout, pattern state grows forever
- Memory leak: Old patterns never expire
- Always use `.within()` unless pattern is instant

---

### 4. **Incorrect Quantifiers**

❌ **Problem:**
```java
// Want 3 or more views
Pattern<Event, ?> pattern = Pattern
    .<Event>begin("views")
    .where(e -> e.type.equals("VIEW"))
    .times(3);  // ❌ Exactly 3 (not 3+)

// 5 views → No match! (only matches exactly 3)
```

✅ **Solution:**
```java
// Use times(3, Integer.MAX_VALUE) for 3 or more
Pattern<Event, ?> pattern = Pattern
    .<Event>begin("views")
    .where(e -> e.type.equals("VIEW"))
    .times(3, Integer.MAX_VALUE);  // ✅ 3 or more

// Or use oneOrMore() after times(2)
Pattern<Event, ?> pattern2 = Pattern
    .<Event>begin("views")
    .where(e -> e.type.equals("VIEW"))
    .times(2)  // First 2
    .followedBy("more-views")
    .where(e -> e.type.equals("VIEW"))
    .oneOrMore();  // Then 1 or more
```

**Quantifier options:**
- `.times(3)`: Exactly 3
- `.times(2, 5)`: Between 2 and 5
- `.times(3, Integer.MAX_VALUE)`: 3 or more
- `.oneOrMore()`: 1 or more
- `.optional()`: 0 or 1

---

### 5. **Pattern Extraction Errors**

❌ **Problem:**
```java
@Override
public Alert select(Map<String, List<Event>> pattern) {
    // Typo in pattern name!
    List<Event> events = pattern.get("add-to-carts");  // ❌ Typo: "add-to-carts"
    // Pattern was defined as "add-to-cart"

    // events is null → NullPointerException!
    return new Alert(events.get(0).userId);
}
```

✅ **Solution:**
```java
// Use constants for pattern names
private static final String ADD_TO_CART_PATTERN = "add-to-cart";
private static final String PURCHASE_PATTERN = "purchase";

Pattern<Event, ?> pattern = Pattern
    .<Event>begin(ADD_TO_CART_PATTERN)  // ✅ Use constant
    // ...

@Override
public Alert select(Map<String, List<Event>> pattern) {
    List<Event> events = pattern.get(ADD_TO_CART_PATTERN);  // ✅ Same constant

    if (events == null || events.isEmpty()) {
        LOG.error("Pattern {} not found in map: {}", ADD_TO_CART_PATTERN, pattern.keySet());
        return null;
    }

    return new Alert(events.get(0).userId);
}
```

---

## 🚀 Performance Tips

### 1. **Use Strict Contiguity When Possible**

❌ **Inefficient:**
```java
// Relaxed contiguity requires more state
Pattern<Event, ?> pattern = Pattern
    .<Event>begin("a")
    .where(...)
    .followedBy("b")  // Buffers all events until match
    .where(...);
```

✅ **Efficient:**
```java
// Strict contiguity = less state
Pattern<Event, ?> pattern = Pattern
    .<Event>begin("a")
    .where(...)
    .next("b")  // Only checks next event
    .where(...);
```

**Rule:**
- Use `.next()` when you know events are consecutive
- Use `.followedBy()` only when events can be interspersed

---

### 2. **Limit Pattern Complexity**

❌ **Complex (slow):**
```java
// 10-step pattern with relaxed contiguity
Pattern<Event, ?> pattern = Pattern
    .<Event>begin("step1")
    .followedBy("step2")
    .followedBy("step3")
    .followedBy("step4")
    .followedBy("step5")
    .followedBy("step6")
    .followedBy("step7")
    .followedBy("step8")
    .followedBy("step9")
    .followedBy("step10")
    .within(Time.hours(1));

// State explosion: Buffers ALL events for 1 hour!
```

✅ **Simplified (fast):**
```java
// Break into smaller patterns
Pattern<Event, ?> pattern1 = Pattern
    .<Event>begin("step1")
    .next("step2")
    .next("step3")
    .within(Time.minutes(10));

// Feed output to second pattern
Pattern<IntermediateResult, ?> pattern2 = Pattern
    .<IntermediateResult>begin("step4")
    .next("step5")
    .next("step6")
    .within(Time.minutes(10));
```

**Guidelines:**
- Limit patterns to 3-5 steps
- Use strict contiguity when possible
- Break complex patterns into stages

---

### 3. **Tune State TTL**

```java
// Configure state TTL to clean up old patterns
StateTtlConfig ttlConfig = StateTtlConfig
    .newBuilder(Time.hours(1))
    .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
    .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
    .build();

// Apply to CEP operator
// (Flink handles this automatically with .within(), but you can tune it)
```

---

## 🌍 Real-World Applications

### 1. **Cart Abandonment (This Example!)**

**Use Case:** E-commerce cart recovery

**Pattern:**
```
ADD_TO_CART+ → (no PURCHASE) within 30 min
```

**Action:**
- Send email: "You left items in your cart!"
- Offer discount: 10% off if purchased within 24 hours
- Track recovery rate: How many abandoned carts convert?

**Metrics:**
```
Cart Abandonment Rate: 68.5%
Recovery Email Sent: 12,543
Recovered Carts: 2,134 (17.0%)
Additional Revenue: $127,456
```

---

### 2. **Fraud Detection**

**Use Case:** Credit card fraud prevention

**Patterns:**
```java
// Pattern 1: Velocity fraud (too many transactions)
Pattern<Transaction, ?> velocityFraud = Pattern
    .<Transaction>begin("purchases")
    .where(tx -> tx.amount > 0)
    .times(5)  // 5 transactions
    .within(Time.minutes(10));  // Within 10 minutes

// Pattern 2: Location fraud (impossible travel)
Pattern<Transaction, ?> locationFraud = Pattern
    .<Transaction>begin("tx1")
    .where(tx -> tx.location.equals("USA"))
    .next("tx2")
    .where(tx -> tx.location.equals("Russia"))
    .within(Time.minutes(30));  // Can't travel USA→Russia in 30 min

// Pattern 3: Card testing (small amounts before large purchase)
Pattern<Transaction, ?> cardTesting = Pattern
    .<Transaction>begin("small")
    .where(tx -> tx.amount <= 1.0)
    .times(3, 10)  // 3-10 small transactions
    .followedBy("large")
    .where(tx -> tx.amount > 500.0)
    .within(Time.hours(1));
```

**Actions:**
- Block transaction automatically
- Send SMS verification code
- Flag for manual review
- Deactivate card

---

### 3. **User Onboarding Tracking**

**Use Case:** SaaS product onboarding funnel

**Pattern:**
```java
// Ideal onboarding flow
Pattern<UserEvent, ?> onboardingPattern = Pattern
    .<UserEvent>begin("signup")
    .where(e -> e.type.equals("ACCOUNT_CREATED"))
    .followedBy("verify-email")
    .where(e -> e.type.equals("EMAIL_VERIFIED"))
    .followedBy("first-action")
    .where(e -> e.type.equals("CREATED_PROJECT"))
    .followedBy("activation")
    .where(e -> e.type.equals("INVITED_TEAM_MEMBER"))
    .within(Time.days(7));  // Within 7 days

// Detect drop-off points
Pattern<UserEvent, ?> dropOffPattern = Pattern
    .<UserEvent>begin("signup")
    .where(e -> e.type.equals("ACCOUNT_CREATED"))
    .followedBy("no-verify")
    .where(e -> !e.type.equals("EMAIL_VERIFIED"))
    .within(Time.days(1));  // Didn't verify within 24 hours
```

**Actions:**
- Send reminder emails at drop-off points
- In-app prompts for next onboarding step
- Sales team follow-up for high-value accounts

**Metrics:**
```
Onboarding Completion Rate: 45%
Drop-off Points:
  - Email verification: 25% (send reminder)
  - First project: 18% (in-app tutorial)
  - Team invitation: 12% (sales outreach)
```

---

### 4. **Manufacturing Process Monitoring**

**Use Case:** Detect assembly line errors

**Pattern:**
```java
// Normal assembly sequence
Pattern<SensorEvent, ?> normalFlow = Pattern
    .<SensorEvent>begin("part-picked")
    .where(e -> e.station.equals("PICK"))
    .next("part-inspected")
    .where(e -> e.station.equals("INSPECT") && e.status.equals("PASS"))
    .next("part-assembled")
    .where(e -> e.station.equals("ASSEMBLE"))
    .next("quality-check")
    .where(e -> e.station.equals("QA") && e.status.equals("PASS"))
    .within(Time.minutes(5));

// Error pattern: Skipped inspection
Pattern<SensorEvent, ?> skippedInspection = Pattern
    .<SensorEvent>begin("pick")
    .where(e -> e.station.equals("PICK"))
    .next("assemble")  // Directly to assembly (skipped inspection!)
    .where(e -> e.station.equals("ASSEMBLE"))
    .within(Time.minutes(5));
```

**Actions:**
- Stop assembly line immediately
- Alert quality control team
- Quarantine affected products
- Log incident for compliance

---

### 5. **Network Security**

**Use Case:** Intrusion detection

**Patterns:**
```java
// Port scan detection
Pattern<NetworkEvent, ?> portScan = Pattern
    .<NetworkEvent>begin("connections")
    .where(e -> e.type.equals("TCP_SYN"))
    .times(10, Integer.MAX_VALUE)  // 10+ connection attempts
    .where(new IterativeCondition<NetworkEvent>() {
        @Override
        public boolean filter(NetworkEvent event, Context<NetworkEvent> ctx) {
            // Same source IP, different destination ports
            Set<Integer> ports = new HashSet<>();
            for (NetworkEvent e : ctx.getEventsForPattern("connections")) {
                ports.add(e.destPort);
            }
            return ports.size() >= 10;  // Scanned 10+ different ports
        }
    })
    .within(Time.seconds(30));

// Brute force attack
Pattern<NetworkEvent, ?> bruteForce = Pattern
    .<NetworkEvent>begin("failed-logins")
    .where(e -> e.type.equals("LOGIN_FAILED"))
    .times(5)  // 5 failed login attempts
    .followedBy("success")
    .where(e -> e.type.equals("LOGIN_SUCCESS"))
    .within(Time.minutes(5));  // Successful login after 5 failures
```

**Actions:**
- Block source IP automatically
- Rate limit login attempts
- Send alert to security team
- Log for forensic analysis

---

## 📊 Debugging & Monitoring

### Logging Pattern Matches

```java
// Add logging to pattern select function
DataStream<Alert> alerts = patternStream.select(
    new PatternSelectFunction<Event, Alert>() {

        @Override
        public Alert select(Map<String, List<Event>> pattern) throws Exception {
            LOG.info("Pattern matched! Keys: {}", pattern.keySet());

            for (Map.Entry<String, List<Event>> entry : pattern.entrySet()) {
                LOG.info("  {}: {} events", entry.getKey(), entry.getValue().size());
                for (Event e : entry.getValue()) {
                    LOG.debug("    - {}", e);
                }
            }

            // Create alert...
            Alert alert = new Alert();
            // ...

            LOG.info("Emitting alert: {}", alert);
            return alert;
        }
    }
);
```

---

### Metrics

```java
// Track pattern match rates
public class MonitoredPatternSelectFunction
        extends RichPatternSelectFunction<Event, Alert> {

    private transient Counter matchCounter;
    private transient Meter matchRate;

    @Override
    public void open(Configuration config) {
        matchCounter = getRuntimeContext()
            .getMetricGroup()
            .counter("pattern_matches");

        matchRate = getRuntimeContext()
            .getMetricGroup()
            .meter("pattern_match_rate", new MeterView(60));  // Per minute
    }

    @Override
    public Alert select(Map<String, List<Event>> pattern) {
        matchCounter.inc();
        matchRate.markEvent();

        // ... create alert ...
        return alert;
    }
}
```

**Key metrics to track:**
- `pattern_matches`: Total matches
- `pattern_match_rate`: Matches per minute
- `pattern_state_size`: Memory usage
- `pattern_timeout_rate`: How many patterns expire

---

## ❓ Quiz Questions

### Question 1: next() vs followedBy()

What's the difference between `.next()` and `.followedBy()`?

A) next() is faster
B) next() requires immediate next event, followedBy() allows events between
C) followedBy() is deprecated
D) They're the same

<details>
<summary>💡 Click to see answer</summary>

**Answer: B) next() requires immediate next event, followedBy() allows events between**

**Explanation:**

**`.next()` (Strict Contiguity):**
```java
Pattern: A .next("b") B

Event sequence: A, B, C
Match: ✅ A → B

Event sequence: A, X, B
Match: ❌ (X breaks strict contiguity)
```

**`.followedBy()` (Relaxed Contiguity):**
```java
Pattern: A .followedBy("b") B

Event sequence: A, B, C
Match: ✅ A → B

Event sequence: A, X, B
Match: ✅ A → B (X is ignored)
```

**When to use:**
- `.next()`: Events are consecutive (faster, less state)
- `.followedBy()`: Events can be interspersed (more flexible, more state)
</details>

---

### Question 2: Time Constraints

What happens if you don't specify `.within()`?

A) Pattern uses default 1-hour timeout
B) Pattern never expires (memory leak)
C) Compile error
D) Runtime exception

<details>
<summary>💡 Click to see answer</summary>

**Answer: B) Pattern never expires (memory leak)**

**Explanation:**

**Without `.within()`:**
```java
Pattern<Event, ?> pattern = Pattern
    .<Event>begin("add")
    .where(e -> e.type.equals("ADD_TO_CART"))
    .followedBy("purchase")
    .where(e -> e.type.equals("PURCHASE"));
    // Missing: .within(Time.minutes(30))

// Pattern state grows forever:
// - User adds to cart on Jan 1
// - No purchase on Jan 1, Jan 2, Jan 3... Dec 31
// - Pattern state kept for entire year → MEMORY LEAK
```

**With `.within()`:**
```java
Pattern<Event, ?> pattern = Pattern
    .<Event>begin("add")
    .where(e -> e.type.equals("ADD_TO_CART"))
    .followedBy("purchase")
    .where(e -> e.type.equals("PURCHASE"))
    .within(Time.minutes(30));  // ✅ Pattern expires after 30 min

// Pattern state cleaned up after 30 minutes
```

**Rule:** ALWAYS use `.within()` unless pattern completes instantly
</details>

---

### Question 3: Keyed Streams

Why must CEP be applied to keyed streams?

A) It doesn't - works on any stream
B) Patterns match per key (user, session, etc.)
C) Performance optimization
D) Flink limitation

<details>
<summary>💡 Click to see answer</summary>

**Answer: B) Patterns match per key (user, session, etc.)**

**Explanation:**

**CEP tracks pattern state per key:**

```
User A: VIEW → ADD → [waiting for PURCHASE]
User B: VIEW → VIEW → VIEW → [waiting for ADD]
User C: ADD → PURCHASE → [pattern completed]

Each user has independent pattern matching!
```

**Without keying:**
```
All events mixed together:
  User A: VIEW
  User B: VIEW
  User A: ADD
  User B: VIEW
  User C: ADD
  User C: PURCHASE

Pattern: VIEW → ADD → PURCHASE
  ↓
Matches User A's VIEW + User C's ADD + User C's PURCHASE
  ❌ Wrong! Mixing different users
```

**With keying:**
```
Key by user ID:
  User A: VIEW → ADD → [waiting]
  User B: VIEW → VIEW → VIEW → [waiting]
  User C: ADD → PURCHASE ✅

Each key has its own pattern state
```
</details>

---

### Question 4: Quantifiers

How do you match "3 or more" events?

A) `.times(3)`
B) `.times(3).orMore()`
C) `.times(3, Integer.MAX_VALUE)`
D) `.oneOrMore().times(3)`

<details>
<summary>💡 Click to see answer</summary>

**Answer: C) `.times(3, Integer.MAX_VALUE)`**

**Explanation:**

**Quantifier options:**

```java
// Exactly 3
.times(3)
// Matches: 3 events
// Doesn't match: 2, 4, 5...

// Between 2 and 5
.times(2, 5)
// Matches: 2, 3, 4, 5 events
// Doesn't match: 1, 6, 7...

// 3 or more
.times(3, Integer.MAX_VALUE)
// Matches: 3, 4, 5, 6, ..., infinity
// Doesn't match: 1, 2

// 1 or more (shorthand)
.oneOrMore()
// Equivalent to: .times(1, Integer.MAX_VALUE)

// 0 or 1
.optional()
// Equivalent to: .times(0, 1)
```

**No `.orMore()` method exists!** Use `.times(min, Integer.MAX_VALUE)` instead.
</details>

---

### Question 5: Pattern Complexity

Which pattern uses LESS memory?

A) `begin("a").next("b").next("c")`
B) `begin("a").followedBy("b").followedBy("c")`

<details>
<summary>💡 Click to see answer</summary>

**Answer: A) `begin("a").next("b").next("c")`**

**Explanation:**

**`.next()` (Strict Contiguity) - Less Memory:**
```java
Pattern: A .next() B .next() C

Event sequence: A, X, Y, Z, B
  ↓
Pattern fails immediately when seeing X (not B)
State cleared → Low memory
```

**`.followedBy()` (Relaxed Contiguity) - More Memory:**
```java
Pattern: A .followedBy() B .followedBy() C

Event sequence: A, X, Y, Z, B
  ↓
Pattern buffers X, Y, Z (in case they're needed later)
Waits for B, then waits for C
State grows → High memory
```

**Memory usage:**
- `.next()`: O(1) - only current event
- `.followedBy()`: O(n) - buffers intermediate events

**Rule:** Use `.next()` when possible (strict contiguity = less memory)
</details>

---

## 🎯 Summary

### Key Takeaways

✅ **CEP** detects sequential event patterns declaratively

✅ **Pattern API** uses begin, next/followedBy, where, oneOrMore, within

✅ **Contiguity** matters: next() (strict) vs followedBy() (relaxed)

✅ **Time constraints** required: Always use `.within()` to prevent memory leaks

✅ **Keyed streams** required: Patterns match per key (user, session, etc.)

✅ **Use cases:** Cart abandonment, fraud detection, process monitoring, security

✅ **Performance:** Prefer strict contiguity, limit pattern steps, tune state TTL

---

### Next Steps

1. ✅ **Run the example:** `CEPCartAbandonmentExample.java`
2. ✅ **Complete exercises:** Value threshold, multi-pattern, fraud detection
3. 📚 **Learn more patterns:**
   - Pattern 01: Session Windows
   - Pattern 02: Broadcast State
4. 🔗 **Combine patterns:** See `BasketAnalysisJob.java` for pattern composition

---

## 📚 Further Reading

- [Flink CEP Docs](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/libs/cep/)
- [CEP Pattern API](https://nightlies.apache.org/flink/flink-docs-release-1.20/api/java/org/apache/flink/cep/pattern/Pattern.html)
- [Pattern Detection Examples](https://github.com/apache/flink/tree/master/flink-libraries/flink-cep/src/test/java/org/apache/flink/cep)

---

<div align="center">
  <b>🎓 Pattern 03: Complex Event Processing (CEP) - Complete!</b><br>
  <i>All 3 core Flink patterns covered - Ready for production!</i>
</div>
