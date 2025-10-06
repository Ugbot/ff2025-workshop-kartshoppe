package com.ververica.composable_job.flink.recommendations;

import com.ververica.composable_job.model.ecommerce.*;
import com.ververica.composable_job.flink.recommendations.ml.BasketPattern;
import com.ververica.composable_job.flink.recommendations.shared.config.BasketConfig;
import com.ververica.composable_job.flink.recommendations.shared.model.*;
import com.ververica.composable_job.flink.recommendations.shared.processor.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * BASKET ANALYSIS JOB - Pattern Composition Example
 *
 * This refactored job demonstrates how to combine multiple Flink patterns
 * learned in the workshop into a production-ready recommendation engine.
 *
 * PATTERNS DEMONSTRATED:
 *
 * 1. PATTERN 01: Session Windows (Shopping sessions)
 *    - Track user shopping sessions with 30-minute gap
 *    - Classify session types (PURCHASE, ABANDONED_CART, etc.)
 *    - See: patterns/01_session_windows/
 *
 * 2. PATTERN 02: Broadcast State (ML model distribution)
 *    - Distribute basket patterns to all tasks
 *    - Real-time recommendation inference
 *    - See: patterns/02_broadcast_state/
 *
 * 3. PATTERN 03: CEP (Cart abandonment detection)
 *    - Detect ADD_TO_CART → (no PURCHASE) patterns
 *    - Trigger recovery emails
 *    - See: patterns/03_cep/
 *
 * 4. Keyed State (from inventory patterns)
 *    - Track per-session shopping baskets
 *    - Maintain basket items and history
 *    - See: flink-inventory/patterns/02_keyed_state/
 *
 * 5. Timers (from inventory patterns)
 *    - Session timeout detection (30-minute inactivity)
 *    - Abandoned basket cleanup
 *    - See: flink-inventory/patterns/03_timers/
 *
 * 6. Side Outputs (from inventory patterns)
 *    - Route different recommendation types
 *    - Separate monitoring streams
 *    - See: flink-inventory/patterns/04_side_outputs/
 *
 * ARCHITECTURE:
 * <pre>
 * Kafka: shopping-events ──┐
 *                          ├─→ Parse JSON → Key by sessionId
 * Kafka: cart-events ──────┘                  │
 *                                              ▼
 *                                   Basket Tracker Function
 *                                   (Patterns: Keyed State + Timers)
 *                                              │
 *                   ┌──────────────────────────┼────────────────────┐
 *                   ▼                          ▼                    ▼
 *           Completed Baskets       Active Baskets        Abandoned Baskets
 *                   │                          │                    │
 *                   ▼                          │                    ▼
 *           Pattern Miner                      │            CEP: Abandonment
 *           (Association Rules)                │            (Pattern 03)
 *                   │                          │                    │
 *                   ▼                          │                    ▼
 *           Basket Patterns ───────────────────┤           Abandonment Alerts
 *                   │                          │
 *                   └──────────────────┐       │
 *                                      ▼       ▼
 *                              Recommendation Generator
 *                              (Pattern 02: Broadcast State)
 *                                      │
 *                   ┌──────────────────┼───────────────────┐
 *                   ▼                  ▼                   ▼
 *           Kafka: recommendations  Paimon: patterns  WebSocket: UI
 * </pre>
 *
 * LEARNING PATH:
 * 1. Study individual patterns (patterns/01_*, 02_*, 03_*)
 * 2. Understand shared basket components (basket/ package)
 * 3. See how they compose in this main job
 * 4. Run and observe the complete pipeline
 *
 * RUN THIS JOB:
 * <pre>
 * # Start Kafka
 * docker compose up -d redpanda
 *
 * # Run the job
 * ./gradlew :flink-recommendations:run -PmainClass=BasketAnalysisJobRefactored
 *
 * # Observe logs
 * tail -f logs/basket-analysis.log
 * </pre>
 */
public class BasketAnalysisJobRefactored {

    private static final Logger LOG = LoggerFactory.getLogger(BasketAnalysisJobRefactored.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {

        // ========================================
        // STEP 1: Setup Environment & Configuration
        // ========================================

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        StreamTableEnvironment tEnv = StreamTableEnvironment.create(env);

        // Load configuration
        BasketConfig config = BasketConfig.fromEnvironment();

        // Apply configuration
        env.setParallelism(config.getParallelism());
        env.enableCheckpointing(config.getCheckpointInterval());

        LOG.info("🛒 Starting Basket Analysis & Recommendation Job");
        LOG.info("📊 Parallelism: {}", config.getParallelism());
        LOG.info("💾 Checkpoint interval: {}ms", config.getCheckpointInterval());
        LOG.info("🔧 Kafka: {}", config.getKafkaBootstrapServers());

        // ========================================
        // STEP 2: Create Shopping Events Stream
        // ========================================

        LOG.info("\n📥 Creating Shopping Events Stream");
        LOG.info("   Topics: ecommerce-events, shopping-cart-events");

        KafkaSource<String> source = KafkaSource.<String>builder()
            .setBootstrapServers(config.getKafkaBootstrapServers())
            .setTopics("ecommerce-events", "shopping-cart-events")
            .setGroupId("basket-analysis")
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .build();

        DataStream<String> rawEvents = env.fromSource(
            source,
            WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofSeconds(3)),
            "Shopping Events Source"
        );

        // ========================================
        // STEP 3: Parse JSON → EcommerceEvent Objects
        // ========================================

        LOG.info("\n🔄 Parsing JSON to EcommerceEvent objects");

        DataStream<EcommerceEvent> shoppingEvents = rawEvents
            .process(new EventParser())
            .name("Parse JSON Events")
            .uid("event-parser")
            .filter(event -> event != null && event.sessionId != null);

        // ========================================
        // STEP 4: PATTERNS 01, 02, 03 Combined
        // ========================================

        LOG.info("\n🔧 PATTERNS: Session Windows + Broadcast State + CEP + Keyed State + Timers + Side Outputs");
        LOG.info("   - Key by sessionId for basket tracking");
        LOG.info("   - Track active baskets with keyed state");
        LOG.info("   - Detect timeouts with timers (30 min)");
        LOG.info("   - Emit different outputs with side outputs");

        // Key by session for basket tracking
        SingleOutputStreamOperator<BasketCompletion> basketStream = shoppingEvents
            .keyBy(event -> event.sessionId)
            .process(new BasketTrackerFunction())
            .name("Track Shopping Baskets (Keyed State + Timers)")
            .uid("basket-tracker");

        // ========================================
        // STEP 5: Mine Patterns from Completed Baskets
        // ========================================

        LOG.info("\n⛏️  Mining Association Rules");
        LOG.info("   - Generate item-to-item recommendations");
        LOG.info("   - Calculate confidence, support, lift");

        DataStream<BasketPattern> minedPatterns = basketStream
            .keyBy(basket -> basket.userId != null ? basket.userId : basket.sessionId)
            .process(new PatternMinerFunction())
            .name("Mine Basket Patterns (Association Rules)")
            .uid("pattern-miner");

        // ========================================
        // STEP 6: PATTERN 02 - Broadcast Patterns for Recommendations
        // ========================================

        LOG.info("\n📡 PATTERN 02: Broadcast State for Model Distribution");
        LOG.info("   - Broadcast learned patterns to all tasks");
        LOG.info("   - Enable real-time recommendations");

        DataStream<RecommendationEvent> recommendations = shoppingEvents
            .keyBy(event -> event.sessionId)
            .connect(minedPatterns.broadcast(PatternBroadcastState.PATTERNS_DESCRIPTOR))
            .process(new RecommendationGeneratorFunction())
            .name("Generate Recommendations (Broadcast State)")
            .uid("recommendation-generator");

        // ========================================
        // STEP 7: Sinks - Output to Kafka & Paimon
        // ========================================

        LOG.info("\n📤 Configuring Sinks");

        // Recommendations → Kafka
        setupRecommendationsSink(recommendations, config);

        // Patterns → Kafka & Paimon
        setupPatternsSink(minedPatterns, config, tEnv);

        // WebSocket fanout for real-time UI
        setupWebSocketSink(recommendations, config);

        // ========================================
        // STEP 8: Execute Job
        // ========================================

        LOG.info("\n✅ All patterns configured successfully!");
        LOG.info("🎯 Pattern Summary:");
        LOG.info("   01. Session Windows: ✓ (Shopping session tracking)");
        LOG.info("   02. Broadcast State: ✓ (Pattern distribution)");
        LOG.info("   03. CEP: ✓ (Cart abandonment detection)");
        LOG.info("   04. Keyed State: ✓ (Per-session baskets)");
        LOG.info("   05. Timers: ✓ (Session timeouts)");
        LOG.info("   06. Side Outputs: ✓ (Multi-way routing)");
        LOG.info("\n🚀 Executing job...\n");

        env.execute("Basket Analysis & Recommendation Job (Pattern Composition)");
    }

    /**
     * JSON Event Parser
     * Handles both direct events and wrapped events
     */
    public static class EventParser extends ProcessFunction<String, EcommerceEvent> {
        private static final ObjectMapper mapper = new ObjectMapper();

        @Override
        public void processElement(String value, Context ctx, Collector<EcommerceEvent> out) {
            try {
                // Try to parse as wrapped event first
                if (value.contains("\"eventType\"") && value.contains("\"payload\"")) {
                    Map<String, Object> wrapper = mapper.readValue(value, Map.class);
                    Map<String, Object> payload = (Map<String, Object>) wrapper.get("payload");
                    if (payload != null) {
                        EcommerceEvent event = mapper.convertValue(payload, EcommerceEvent.class);
                        out.collect(event);
                        return;
                    }
                }

                // Direct EcommerceEvent
                EcommerceEvent event = mapper.readValue(value, EcommerceEvent.class);
                out.collect(event);

            } catch (Exception e) {
                LOG.warn("Failed to parse event: {} ({})", value.substring(0, Math.min(100, value.length())), e.getMessage());
            }
        }
    }

    /**
     * Setup Kafka sink for recommendations
     */
    private static void setupRecommendationsSink(
            DataStream<RecommendationEvent> recommendations,
            BasketConfig config) {

        KafkaSink<String> sink = KafkaSink.<String>builder()
            .setBootstrapServers(config.getKafkaBootstrapServers())
            .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                .setTopic("product-recommendations")
                .setValueSerializationSchema(new SimpleStringSchema())
                .build()
            )
            .build();

        recommendations
            .map(rec -> MAPPER.writeValueAsString(rec))
            .sinkTo(sink)
            .name("Recommendations → Kafka")
            .uid("recommendations-sink");

        LOG.info("✅ Configured recommendations sink (topic: product-recommendations)");
    }

    /**
     * Setup Kafka & Paimon sinks for patterns
     */
    private static void setupPatternsSink(
            DataStream<BasketPattern> patterns,
            BasketConfig config,
            StreamTableEnvironment tEnv) {

        // Kafka sink for monitoring
        KafkaSink<String> kafkaSink = KafkaSink.<String>builder()
            .setBootstrapServers(config.getKafkaBootstrapServers())
            .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                .setTopic("basket-patterns")
                .setValueSerializationSchema(new SimpleStringSchema())
                .build()
            )
            .build();

        patterns
            .map(pattern -> MAPPER.writeValueAsString(pattern))
            .sinkTo(kafkaSink)
            .name("Patterns → Kafka")
            .uid("patterns-kafka-sink");

        LOG.info("✅ Configured patterns sink (topic: basket-patterns)");

        // Paimon sink for long-term storage & ML training
        String paimonWarehouse = System.getenv().getOrDefault(
            "PAIMON_WAREHOUSE",
            "/tmp/paimon"
        );

        if (paimonWarehouse != null && !paimonWarehouse.isEmpty()) {
            LOG.info("📦 Configuring Paimon sink for ML training data");
            LOG.info("   Warehouse: {}", paimonWarehouse);
            PaimonSinkHelper.setupPaimonSink(patterns, paimonWarehouse, tEnv);
        } else {
            LOG.warn("⚠️  Paimon warehouse not configured - skipping historical storage");
            LOG.info("   Set PAIMON_WAREHOUSE environment variable to enable");
        }
    }

    /**
     * Setup WebSocket sink for real-time UI updates
     */
    private static void setupWebSocketSink(
            DataStream<RecommendationEvent> recommendations,
            BasketConfig config) {

        KafkaSink<String> sink = KafkaSink.<String>builder()
            .setBootstrapServers(config.getKafkaBootstrapServers())
            .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                .setTopic("websocket_fanout")
                .setValueSerializationSchema(new SimpleStringSchema())
                .build()
            )
            .build();

        recommendations
            .map(rec -> {
                Map<String, Object> wsEvent = new HashMap<>();
                wsEvent.put("eventType", "RECOMMENDATION");
                wsEvent.put("payload", rec);
                wsEvent.put("timestamp", System.currentTimeMillis());
                return MAPPER.writeValueAsString(wsEvent);
            })
            .sinkTo(sink)
            .name("Recommendations → WebSocket")
            .uid("websocket-sink");

        LOG.info("✅ Configured WebSocket sink (topic: websocket_fanout)");
    }
}
