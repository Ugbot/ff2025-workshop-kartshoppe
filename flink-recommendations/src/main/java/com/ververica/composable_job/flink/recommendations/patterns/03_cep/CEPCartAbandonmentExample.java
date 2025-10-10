package com.ververica.composable_job.flink.recommendations.patterns.cep;

import com.ververica.composable_job.model.ecommerce.EcommerceEvent;
import com.ververica.composable_job.model.ecommerce.EcommerceEventType;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternSelectFunction;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * FLINK PATTERN: Complex Event Processing (CEP)
 *
 * PURPOSE:
 * Detect complex patterns in event streams using declarative pattern matching.
 * Perfect for fraud detection, anomaly detection, and business process monitoring.
 *
 * KEY CONCEPTS:
 * 1. Pattern definition = Sequence of events with conditions
 * 2. Event selection = Match events based on predicates
 * 3. Time constraints = Patterns must complete within time window
 * 4. Quantifiers = One, oneOrMore, times(n), optional
 *
 * WHEN TO USE CEP:
 * - Cart abandonment detection (this example!)
 * - Fraud pattern matching (unusual transaction sequences)
 * - User journey tracking (signup → activation → purchase)
 * - System monitoring (error → warning → critical failure)
 * - Anomaly detection (deviation from normal patterns)
 *
 * VS OTHER PATTERNS:
 * - Session Windows: Group all events in time period (no order)
 * - CEP: Match specific EVENT SEQUENCES (order matters!)
 * - ProcessFunction: Manual state management
 * - CEP: Declarative pattern language
 *
 * REAL-WORLD EXAMPLE: Cart Abandonment
 * User adds items to cart but doesn't purchase:
 *   1. ADD_TO_CART (laptop)
 *   2. ADD_TO_CART (mouse)
 *   3. [No PURCHASE within 30 minutes]
 *   → Trigger abandonment alert
 *
 * PATTERN SYNTAX:
 * <pre>
 * Pattern<Event, ?> pattern = Pattern.<Event>begin("add-to-cart")
 *     .where(new SimpleCondition<Event>() {
 *         public boolean filter(Event event) {
 *             return event.eventType == EcommerceEventType.ADD_TO_CART;
 *         }
 *     })
 *     .oneOrMore()  // One or more items added
 *     .next("no-purchase")  // Followed by...
 *     .where(new SimpleCondition<Event>() {
 *         public boolean filter(Event event) {
 *             return event.eventType == EcommerceEventType.ORDER_PLACED;
 *         }
 *     })
 *     .times(0)  // ZERO purchases (negative match!)
 *     .within(Time.minutes(30));  // Within 30 minutes
 * </pre>
 */
public class CEPCartAbandonmentExample {

    private static final Logger LOG = LoggerFactory.getLogger(CEPCartAbandonmentExample.class);

    // Time window for cart abandonment (30 minutes)
    private static final int ABANDONMENT_WINDOW_MINUTES = 30;

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);

        LOG.info("🛒 Starting CEP Cart Abandonment Detection");
        LOG.info("⏰ Abandonment window: {} minutes", ABANDONMENT_WINDOW_MINUTES);

        // ========================================
        // STEP 1: Create event stream
        // ========================================

        DataStream<EcommerceEvent> events = createSampleEventStream(env);

        // ========================================
        // STEP 2: Assign timestamps and watermarks
        // ========================================

        WatermarkStrategy<EcommerceEvent> watermarkStrategy = WatermarkStrategy
            .<EcommerceEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5))
            .withTimestampAssigner(new SerializableTimestampAssigner<EcommerceEvent>() {
                @Override
                public long extractTimestamp(EcommerceEvent event, long recordTimestamp) {
                    return event.timestamp;
                }
            });

        DataStream<EcommerceEvent> eventStream = events
            .assignTimestampsAndWatermarks(watermarkStrategy)
            .keyBy(event -> event.sessionId);  // Key by session for pattern matching

        // ========================================
        // STEP 3: PATTERN 1 - Cart Abandonment
        // ========================================

        LOG.info("\n🎯 PATTERN 1: Cart Abandonment Detection");
        LOG.info("   Sequence: ADD_TO_CART+ → (no PURCHASE) within 30 min");

        // Pattern: Add to cart, but no purchase within time window
        Pattern<EcommerceEvent, ?> cartAbandonmentPattern = Pattern
            .<EcommerceEvent>begin("add-to-cart")
            .where(new SimpleCondition<EcommerceEvent>() {
                @Override
                public boolean filter(EcommerceEvent event) {
                    return event.eventType == EcommerceEventType.ADD_TO_CART;
                }
            })
            .oneOrMore()  // At least one item added to cart
            .followedBy("view-or-end")  // Followed by anything (or end)
            .where(new SimpleCondition<EcommerceEvent>() {
                @Override
                public boolean filter(EcommerceEvent event) {
                    // Accept any event that's NOT a purchase
                    return event.eventType != EcommerceEventType.ORDER_PLACED;
                }
            })
            .oneOrMore()
            .optional()  // May or may not have other events
            .within(Time.minutes(ABANDONMENT_WINDOW_MINUTES));

        // Apply pattern to stream
        PatternStream<EcommerceEvent> abandonmentPatternStream = CEP.pattern(
            eventStream,
            cartAbandonmentPattern
        );

        // Extract matched patterns
        DataStream<CartAbandonmentAlert> abandonmentAlerts = abandonmentPatternStream
            .select(new AbandonmentPatternSelectFunction())
            .name("Cart Abandonment Alerts");

        // ========================================
        // STEP 4: PATTERN 2 - Quick Purchase (Success)
        // ========================================

        LOG.info("\n🎯 PATTERN 2: Quick Purchase Detection");
        LOG.info("   Sequence: VIEW_PRODUCT → ADD_TO_CART → PURCHASE within 10 min");

        // Pattern: View → Add → Purchase (successful conversion)
        Pattern<EcommerceEvent, ?> quickPurchasePattern = Pattern
            .<EcommerceEvent>begin("view")
            .where(new SimpleCondition<EcommerceEvent>() {
                @Override
                public boolean filter(EcommerceEvent event) {
                    return event.eventType == EcommerceEventType.PRODUCT_VIEW;
                }
            })
            .next("add")  // Strict contiguity (no events between)
            .where(new SimpleCondition<EcommerceEvent>() {
                @Override
                public boolean filter(EcommerceEvent event) {
                    return event.eventType == EcommerceEventType.ADD_TO_CART;
                }
            })
            .next("purchase")
            .where(new SimpleCondition<EcommerceEvent>() {
                @Override
                public boolean filter(EcommerceEvent event) {
                    return event.eventType == EcommerceEventType.ORDER_PLACED;
                }
            })
            .within(Time.minutes(10));  // Within 10 minutes

        PatternStream<EcommerceEvent> quickPurchasePatternStream = CEP.pattern(
            eventStream,
            quickPurchasePattern
        );

        DataStream<QuickPurchaseEvent> quickPurchases = quickPurchasePatternStream
            .select(new QuickPurchasePatternSelectFunction())
            .name("Quick Purchase Detections");

        // ========================================
        // STEP 5: PATTERN 3 - Repeated Browsing (No Action)
        // ========================================

        LOG.info("\n🎯 PATTERN 3: Repeated Browsing Detection");
        LOG.info("   Sequence: VIEW_PRODUCT{3,} → (no ADD_TO_CART) within 20 min");

        // Pattern: Multiple views but no add to cart (window shopping)
        Pattern<EcommerceEvent, ?> repeatedBrowsingPattern = Pattern
            .<EcommerceEvent>begin("views")
            .where(new SimpleCondition<EcommerceEvent>() {
                @Override
                public boolean filter(EcommerceEvent event) {
                    return event.eventType == EcommerceEventType.PRODUCT_VIEW;
                }
            })
            .times(3, 10)  // Between 3 and 10 views
            .within(Time.minutes(20));

        PatternStream<EcommerceEvent> repeatedBrowsingPatternStream = CEP.pattern(
            eventStream,
            repeatedBrowsingPattern
        );

        DataStream<RepeatedBrowsingAlert> browsingAlerts = repeatedBrowsingPatternStream
            .select(new RepeatedBrowsingPatternSelectFunction())
            .name("Repeated Browsing Alerts");

        // ========================================
        // STEP 6: Print Results
        // ========================================

        abandonmentAlerts.print().name("Print Abandonment Alerts");
        quickPurchases.print().name("Print Quick Purchases");
        browsingAlerts.print().name("Print Browsing Alerts");

        env.execute("CEP Cart Abandonment Detection");
    }

    /**
     * Pattern select function for cart abandonment.
     * Extracts matched events and creates alert.
     */
    public static class AbandonmentPatternSelectFunction
            implements PatternSelectFunction<EcommerceEvent, CartAbandonmentAlert> {

        @Override
        public CartAbandonmentAlert select(Map<String, List<EcommerceEvent>> pattern) {
            List<EcommerceEvent> cartEvents = pattern.get("add-to-cart");

            if (cartEvents == null || cartEvents.isEmpty()) {
                return null;
            }

            CartAbandonmentAlert alert = new CartAbandonmentAlert();
            alert.sessionId = cartEvents.get(0).sessionId;
            alert.userId = cartEvents.get(0).userId;
            alert.itemsInCart = new ArrayList<>();
            alert.totalItems = cartEvents.size();

            // Collect all products added to cart
            for (EcommerceEvent event : cartEvents) {
                alert.itemsInCart.add(event.productId);
            }

            alert.firstAddTime = cartEvents.get(0).timestamp;
            alert.lastAddTime = cartEvents.get(cartEvents.size() - 1).timestamp;
            alert.abandonmentTime = System.currentTimeMillis();

            LOG.warn("🛒 CART ABANDONED: Session {} with {} items: {}",
                alert.sessionId, alert.totalItems, alert.itemsInCart);

            return alert;
        }
    }

    /**
     * Pattern select function for quick purchases.
     */
    public static class QuickPurchasePatternSelectFunction
            implements PatternSelectFunction<EcommerceEvent, QuickPurchaseEvent> {

        @Override
        public QuickPurchaseEvent select(Map<String, List<EcommerceEvent>> pattern) {
            EcommerceEvent viewEvent = pattern.get("view").get(0);
            EcommerceEvent addEvent = pattern.get("add").get(0);
            EcommerceEvent purchaseEvent = pattern.get("purchase").get(0);

            QuickPurchaseEvent quickPurchase = new QuickPurchaseEvent();
            quickPurchase.sessionId = viewEvent.sessionId;
            quickPurchase.userId = viewEvent.userId;
            quickPurchase.productId = purchaseEvent.productId;
            quickPurchase.viewToPurchaseTime = purchaseEvent.timestamp - viewEvent.timestamp;

            LOG.info("🎯 QUICK PURCHASE: User {} bought {} in {} seconds",
                quickPurchase.userId,
                quickPurchase.productId,
                quickPurchase.viewToPurchaseTime / 1000);

            return quickPurchase;
        }
    }

    /**
     * Pattern select function for repeated browsing.
     */
    public static class RepeatedBrowsingPatternSelectFunction
            implements PatternSelectFunction<EcommerceEvent, RepeatedBrowsingAlert> {

        @Override
        public RepeatedBrowsingAlert select(Map<String, List<EcommerceEvent>> pattern) {
            List<EcommerceEvent> viewEvents = pattern.get("views");

            if (viewEvents == null || viewEvents.isEmpty()) {
                return null;
            }

            RepeatedBrowsingAlert alert = new RepeatedBrowsingAlert();
            alert.sessionId = viewEvents.get(0).sessionId;
            alert.userId = viewEvents.get(0).userId;
            alert.viewCount = viewEvents.size();
            alert.viewedProducts = new ArrayList<>();

            for (EcommerceEvent event : viewEvents) {
                alert.viewedProducts.add(event.productId);
            }

            LOG.info("👀 REPEATED BROWSING: User {} viewed {} products: {}",
                alert.userId, alert.viewCount, alert.viewedProducts);

            return alert;
        }
    }

    // ========================================
    // Alert/Event Classes
    // ========================================

    /**
     * Cart abandonment alert
     */
    public static class CartAbandonmentAlert implements Serializable {
        public String sessionId;
        public String userId;
        public List<String> itemsInCart;
        public int totalItems;
        public long firstAddTime;
        public long lastAddTime;
        public long abandonmentTime;

        @Override
        public String toString() {
            return String.format("🛒 ABANDONED CART: User=%s, Session=%s, Items=%d %s",
                userId, sessionId, totalItems, itemsInCart);
        }
    }

    /**
     * Quick purchase event (successful conversion)
     */
    public static class QuickPurchaseEvent implements Serializable {
        public String sessionId;
        public String userId;
        public String productId;
        public long viewToPurchaseTime;  // milliseconds

        @Override
        public String toString() {
            return String.format("🎯 QUICK PURCHASE: User=%s, Product=%s, Time=%ds",
                userId, productId, viewToPurchaseTime / 1000);
        }
    }

    /**
     * Repeated browsing alert (window shopping)
     */
    public static class RepeatedBrowsingAlert implements Serializable {
        public String sessionId;
        public String userId;
        public int viewCount;
        public List<String> viewedProducts;

        @Override
        public String toString() {
            return String.format("👀 BROWSING: User=%s, Views=%d, Products=%s",
                userId, viewCount, viewedProducts);
        }
    }

    // ========================================
    // Sample Data
    // ========================================

    /**
     * Create sample event stream demonstrating different patterns.
     */
    private static DataStream<EcommerceEvent> createSampleEventStream(StreamExecutionEnvironment env) {
        long baseTime = System.currentTimeMillis();
        List<EcommerceEvent> events = new ArrayList<>();

        // ========================================
        // SCENARIO 1: Cart Abandonment
        // ========================================
        LOG.info("\n📋 SCENARIO 1: Cart Abandonment");
        LOG.info("   User adds items but doesn't purchase");

        events.add(createEvent("session-001", "user-001", EcommerceEventType.PRODUCT_VIEW, "LAPTOP_001", baseTime));
        events.add(createEvent("session-001", "user-001", EcommerceEventType.ADD_TO_CART, "LAPTOP_001", baseTime + 2000));
        events.add(createEvent("session-001", "user-001", EcommerceEventType.PRODUCT_VIEW, "MOUSE_001", baseTime + 5000));
        events.add(createEvent("session-001", "user-001", EcommerceEventType.ADD_TO_CART, "MOUSE_001", baseTime + 7000));
        // No purchase → Cart abandoned!

        // ========================================
        // SCENARIO 2: Quick Purchase (Success!)
        // ========================================
        LOG.info("\n📋 SCENARIO 2: Quick Purchase");
        LOG.info("   User views, adds, and purchases quickly");

        events.add(createEvent("session-002", "user-002", EcommerceEventType.PRODUCT_VIEW, "PHONE_001", baseTime + 10000));
        events.add(createEvent("session-002", "user-002", EcommerceEventType.ADD_TO_CART, "PHONE_001", baseTime + 12000));
        events.add(createEvent("session-002", "user-002", EcommerceEventType.ORDER_PLACED, "PHONE_001", baseTime + 15000));
        // View → Add → Purchase within 5 seconds!

        // ========================================
        // SCENARIO 3: Repeated Browsing (Window Shopping)
        // ========================================
        LOG.info("\n📋 SCENARIO 3: Repeated Browsing");
        LOG.info("   User views many products but doesn't add to cart");

        events.add(createEvent("session-003", "user-003", EcommerceEventType.PRODUCT_VIEW, "KEYBOARD_001", baseTime + 20000));
        events.add(createEvent("session-003", "user-003", EcommerceEventType.PRODUCT_VIEW, "MONITOR_001", baseTime + 22000));
        events.add(createEvent("session-003", "user-003", EcommerceEventType.PRODUCT_VIEW, "HEADPHONES_001", baseTime + 24000));
        events.add(createEvent("session-003", "user-003", EcommerceEventType.PRODUCT_VIEW, "WEBCAM_001", baseTime + 26000));
        events.add(createEvent("session-003", "user-003", EcommerceEventType.PRODUCT_VIEW, "MOUSE_002", baseTime + 28000));
        // 5 views, no add to cart → Window shopping

        // ========================================
        // SCENARIO 4: Mixed Behavior
        // ========================================
        LOG.info("\n📋 SCENARIO 4: Mixed Behavior");
        LOG.info("   User adds to cart, then purchases (successful)");

        events.add(createEvent("session-004", "user-004", EcommerceEventType.PRODUCT_VIEW, "LAPTOP_002", baseTime + 30000));
        events.add(createEvent("session-004", "user-004", EcommerceEventType.ADD_TO_CART, "LAPTOP_002", baseTime + 32000));
        events.add(createEvent("session-004", "user-004", EcommerceEventType.PRODUCT_VIEW, "LAPTOP_CASE_001", baseTime + 35000));
        events.add(createEvent("session-004", "user-004", EcommerceEventType.ADD_TO_CART, "LAPTOP_CASE_001", baseTime + 37000));
        events.add(createEvent("session-004", "user-004", EcommerceEventType.ORDER_PLACED, "LAPTOP_002", baseTime + 40000));
        // Purchased → No abandonment

        return env.fromCollection(events);
    }

    private static EcommerceEvent createEvent(String sessionId, String userId, EcommerceEventType eventType, String productId, long timestamp) {
        EcommerceEvent event = new EcommerceEvent();
        event.sessionId = sessionId;
        event.userId = userId;
        event.eventType = eventType;
        event.productId = productId;
        event.timestamp = timestamp;
        return event;
    }
}
