package com.ververica.composable_job.flink.recommendations.patterns.session_windows;

import com.ververica.composable_job.model.ecommerce.EcommerceEvent;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.EventTimeSessionWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * FLINK PATTERN: Session Windows
 *
 * PURPOSE:
 * Group events by natural user sessions based on inactivity gaps.
 * Perfect for tracking shopping sessions, user journeys, and behavior patterns.
 *
 * KEY CONCEPTS:
 * 1. Session window = activity period separated by gaps of inactivity
 * 2. Gap timeout: 30 minutes of no activity = session ends
 * 3. Automatic merging: Overlapping sessions merge into one
 * 4. Dynamic duration: Each session can be different length
 *
 * WHEN TO USE:
 * - User shopping sessions (this example!)
 * - Device activity bursts (IoT)
 * - Network packet flows
 * - User engagement tracking
 * - Log file analysis by user
 *
 * VS OTHER WINDOWS:
 * - Tumbling: Fixed size, no overlap (e.g., hourly batches)
 * - Sliding: Fixed size, overlaps (e.g., rolling 5-min average)
 * - Session: Dynamic size based on activity (natural user sessions!)
 * - Global: One window for all time (use with custom triggers)
 *
 * REAL-WORLD EXAMPLE:
 * User browses products:
 *   10:00:00 - VIEW laptop
 *   10:05:00 - VIEW mouse
 *   10:10:00 - ADD laptop to cart
 *   ... 30 min gap ...
 *   10:45:00 - Session ends → Window closes
 *
 * New session starts:
 *   11:00:00 - VIEW keyboard
 *   ...
 */
public class SessionWindowExample {

    private static final Logger LOG = LoggerFactory.getLogger(SessionWindowExample.class);

    // Session gap: 30 minutes of inactivity = session ends
    private static final Duration SESSION_GAP = Duration.ofMinutes(30);

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);

        LOG.info("🛍️  Starting Session Window Example");
        LOG.info("📊 Session gap: {} minutes", SESSION_GAP.toMinutes());

        // Create sample shopping events
        DataStream<EcommerceEvent> shoppingEvents = createSampleShoppingStream(env);

        // Define watermark strategy for event time processing
        WatermarkStrategy<EcommerceEvent> watermarkStrategy = WatermarkStrategy
            .<EcommerceEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5))
            .withTimestampAssigner(new SerializableTimestampAssigner<EcommerceEvent>() {
                @Override
                public long extractTimestamp(EcommerceEvent event, long recordTimestamp) {
                    return event.timestamp;
                }
            });

        // Apply session windows
        DataStream<ShoppingSession> sessions = shoppingEvents
            .assignTimestampsAndWatermarks(watermarkStrategy)
            .keyBy(event -> event.sessionId)
            .window(EventTimeSessionWindows.withGap(Time.minutes(30)))
            .process(new SessionAggregator())
            .name("Session Window Aggregation")
            .uid("session-aggregator");

        // Print sessions
        sessions.print();

        env.execute("Session Window Pattern Example");
    }

    /**
     * ProcessWindowFunction that aggregates all events in a session.
     *
     * WINDOW LIFECYCLE:
     * 1. First event → Window created
     * 2. More events arrive → Added to window (if within gap)
     * 3. Gap timeout reached → Window closes
     * 4. process() called with ALL events in window
     * 5. Window state cleaned up
     */
    public static class SessionAggregator
            extends ProcessWindowFunction<EcommerceEvent, ShoppingSession, String, TimeWindow> {

        @Override
        public void process(
                String sessionId,
                Context context,
                Iterable<EcommerceEvent> events,
                Collector<ShoppingSession> out) {

            TimeWindow window = context.window();

            ShoppingSession session = new ShoppingSession();
            session.sessionId = sessionId;
            session.sessionStart = window.getStart();
            session.sessionEnd = window.getEnd();
            session.sessionDuration = (window.getEnd() - window.getStart()) / 1000; // seconds

            // Track event types
            session.viewedProducts = new ArrayList<>();
            session.addedToCart = new ArrayList<>();
            Set<String> uniqueProducts = new HashSet<>();
            int viewCount = 0;
            int addCount = 0;
            boolean hasPurchase = false;

            // Aggregate all events in session
            for (EcommerceEvent event : events) {
                uniqueProducts.add(event.productId);

                switch (event.eventType) {
                    case "VIEW_PRODUCT":
                        session.viewedProducts.add(event.productId);
                        viewCount++;
                        break;
                    case "ADD_TO_CART":
                        session.addedToCart.add(event.productId);
                        addCount++;
                        break;
                    case "PURCHASE":
                    case "CHECKOUT":
                        hasPurchase = true;
                        session.completedPurchase = true;
                        break;
                }
            }

            session.totalEvents = viewCount + addCount + (hasPurchase ? 1 : 0);
            session.uniqueProducts = uniqueProducts.size();
            session.viewCount = viewCount;
            session.addToCartCount = addCount;

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

            LOG.info("🎯 Session complete: {} | Type: {} | Duration: {}s | Events: {} | Products: {}",
                sessionId, session.sessionType, session.sessionDuration,
                session.totalEvents, session.uniqueProducts);

            out.collect(session);
        }
    }

    /**
     * Shopping session summary
     */
    public static class ShoppingSession implements Serializable {
        public String sessionId;
        public long sessionStart;
        public long sessionEnd;
        public long sessionDuration; // seconds
        public String sessionType;
        public int totalEvents;
        public int uniqueProducts;
        public int viewCount;
        public int addToCartCount;
        public boolean completedPurchase;
        public List<String> viewedProducts;
        public List<String> addedToCart;

        @Override
        public String toString() {
            return String.format("[%s] Session=%s, Duration=%ds, Type=%s, Events=%d, Products=%d, Purchase=%s",
                sessionType, sessionId, sessionDuration, sessionType, totalEvents, uniqueProducts, completedPurchase);
        }
    }

    /**
     * Create sample shopping event stream for testing.
     * Simulates 3 different shopping sessions.
     */
    private static DataStream<EcommerceEvent> createSampleShoppingStream(StreamExecutionEnvironment env) {
        long baseTime = System.currentTimeMillis();

        List<EcommerceEvent> events = new ArrayList<>();

        // SESSION 1: Browser (views only, no purchase)
        events.add(createEvent("session-001", "user-001", "VIEW_PRODUCT", "LAPTOP_001", baseTime));
        events.add(createEvent("session-001", "user-001", "VIEW_PRODUCT", "MOUSE_001", baseTime + 5 * 60 * 1000));
        events.add(createEvent("session-001", "user-001", "VIEW_PRODUCT", "KEYBOARD_001", baseTime + 10 * 60 * 1000));
        events.add(createEvent("session-001", "user-001", "VIEW_PRODUCT", "MONITOR_001", baseTime + 12 * 60 * 1000));
        // Session gap > 30 min → session ends

        // SESSION 2: Abandoned cart
        events.add(createEvent("session-002", "user-002", "VIEW_PRODUCT", "PHONE_001", baseTime + 60 * 60 * 1000));
        events.add(createEvent("session-002", "user-002", "VIEW_PRODUCT", "PHONE_CASE_001", baseTime + 62 * 60 * 1000));
        events.add(createEvent("session-002", "user-002", "ADD_TO_CART", "PHONE_001", baseTime + 65 * 60 * 1000));
        events.add(createEvent("session-002", "user-002", "ADD_TO_CART", "PHONE_CASE_001", baseTime + 66 * 60 * 1000));
        // No purchase → abandoned cart

        // SESSION 3: Successful purchase
        events.add(createEvent("session-003", "user-003", "VIEW_PRODUCT", "HEADPHONES_001", baseTime + 120 * 60 * 1000));
        events.add(createEvent("session-003", "user-003", "ADD_TO_CART", "HEADPHONES_001", baseTime + 122 * 60 * 1000));
        events.add(createEvent("session-003", "user-003", "PURCHASE", "HEADPHONES_001", baseTime + 125 * 60 * 1000));

        return env.fromCollection(events);
    }

    private static EcommerceEvent createEvent(String sessionId, String userId, String eventType, String productId, long timestamp) {
        EcommerceEvent event = new EcommerceEvent();
        event.sessionId = sessionId;
        event.userId = userId;
        event.eventType = eventType;
        event.productId = productId;
        event.timestamp = timestamp;
        return event;
    }
}
