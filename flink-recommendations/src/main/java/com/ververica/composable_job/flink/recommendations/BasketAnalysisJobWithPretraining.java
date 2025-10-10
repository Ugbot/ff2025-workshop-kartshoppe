package com.ververica.composable_job.flink.recommendations;

import com.ververica.composable_job.model.ecommerce.*;
import com.ververica.composable_job.flink.recommendations.ml.BasketPattern;
import com.ververica.composable_job.flink.recommendations.shared.config.BasketConfig;
import com.ververica.composable_job.flink.recommendations.shared.model.*;
import com.ververica.composable_job.flink.recommendations.shared.processor.*;
import com.ververica.composable_job.flink.recommendations.source.PaimonKafkaHybridSource;
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
 * BASKET ANALYSIS JOB WITH PRE-TRAINING - Advanced Pattern Composition
 *
 * This job extends BasketAnalysisJobRefactored with hybrid source for cold-start performance.
 *
 * KEY DIFFERENCES FROM BASE JOB:
 *
 * 1. HYBRID SOURCE (Paimon + Kafka):
 *    - Phase 1 (Bounded): Load 5000+ historical patterns from Paimon
 *    - Phase 2 (Unbounded): Stream new patterns from Kafka
 *    - Result: Recommendations work from first shopping event!
 *
 * 2. PATTERN FLOW:
 *    <pre>
 *    Historical Patterns (Paimon)
 *      │
 *      ├─ Read basket_analysis.basket_patterns
 *      ├─ Convert to BasketPattern objects
 *      │
 *      ▼
 *    Broadcast State (5000 patterns loaded immediately)
 *      │
 *      ├─ Used by RecommendationGeneratorFunction
 *      ├─ High-quality recommendations from start
 *      │
 *      ▼
 *    New Patterns (Kafka - online learning)
 *      │
 *      ├─ PatternMinerFunction generates new patterns
 *      ├─ Broadcast to all tasks
 *      │
 *      ▼
 *    Broadcast State (5000+ patterns, continuously growing)
 *    </pre>
 *
 * 3. PERFORMANCE BENEFITS:
 *    - WITHOUT pre-training: 0 patterns → poor recommendations initially
 *    - WITH pre-training: 5000 patterns → excellent recommendations from first event
 *    - Continuous improvement: online learning adds new patterns
 *
 * SETUP REQUIRED:
 * <pre>
 * # Generate historical training data
 * ./init-paimon-training-data.sh
 *
 * # Run job with pre-training
 * export PAIMON_WAREHOUSE=/tmp/paimon
 * ./start-basket-job-with-pretraining.sh
 * </pre>
 *
 * @see BasketAnalysisJobRefactored (base job without pre-training)
 * @see PaimonKafkaHybridSource (hybrid source implementation)
 */
public class BasketAnalysisJobWithPretraining {

    private static final Logger LOG = LoggerFactory.getLogger(BasketAnalysisJobWithPretraining.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {

        // ========================================
        // STEP 1: Setup Environment & Configuration
        // ========================================

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        StreamTableEnvironment tEnv = StreamTableEnvironment.create(env);

        // Load configuration
        BasketConfig config = BasketConfig.fromEnvironment();
        String paimonWarehouse = System.getenv().getOrDefault("PAIMON_WAREHOUSE", "/tmp/paimon");

        // Apply configuration
        env.setParallelism(config.getParallelism());
        env.enableCheckpointing(config.getCheckpointInterval());

        LOG.info("🛒 Starting Basket Analysis Job WITH PRE-TRAINING");
        LOG.info("📊 Parallelism: {}", config.getParallelism());
        LOG.info("💾 Checkpoint interval: {}ms", config.getCheckpointInterval());
        LOG.info("🔧 Kafka: {}", config.getKafkaBootstrapServers());
        LOG.info("📦 Paimon warehouse: {}", paimonWarehouse);
        LOG.info("");
        LOG.info("🎯 MODE: Hybrid Source (Historical Paimon + Live Kafka)");
        LOG.info("   This job will load historical patterns before processing events!");

        // ========================================
        // STEP 2: Create Shopping Events Stream
        // ========================================

        LOG.info("\n📥 Creating Shopping Events Stream");
        LOG.info("   Topics: ecommerce-events, shopping-cart-events");

        KafkaSource<String> source = KafkaSource.<String>builder()
            .setBootstrapServers(config.getKafkaBootstrapServers())
            .setTopics("ecommerce-events", "shopping-cart-events")
            .setGroupId("basket-analysis-pretraining")
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .build();

        DataStream<String> rawEvents = env.fromSource(
            source,
            WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofSeconds(3)),
            "Shopping Events Source"
        );

        // Parse JSON
        DataStream<EcommerceEvent> shoppingEvents = rawEvents
            .process(new EventParser())
            .name("Parse JSON Events")
            .uid("event-parser")
            .filter(event -> event != null && event.sessionId != null);

        // ========================================
        // STEP 3: HYBRID SOURCE - Historical + Live Patterns
        // ========================================

        LOG.info("\n📚 HYBRID SOURCE: Historical Patterns (Paimon) + Live Patterns (Kafka)");
        LOG.info("   Phase 1: Loading historical patterns from Paimon...");
        LOG.info("   Database: basket_analysis");
        LOG.info("   Table: basket_patterns");

        // Create hybrid source for patterns
        DataStream<BasketPattern> allPatterns;

        try {
            allPatterns = PaimonKafkaHybridSource.createHybridPatternSource(
                env,
                paimonWarehouse,
                config.getKafkaBootstrapServers(),
                "basket_analysis",
                "basket_patterns",
                "basket-patterns"
            );
            LOG.info("✅ Hybrid source configured successfully");
            LOG.info("   Historical patterns will be loaded from Paimon");
            LOG.info("   Live patterns will stream from Kafka");
        } catch (Exception e) {
            LOG.warn("⚠️  Failed to create hybrid source: {}", e.getMessage());
            LOG.info("   Falling back to live-only mode (no historical patterns)");
            LOG.info("   Run ./init-paimon-training-data.sh to enable pre-training");

            // Fallback: use only live pattern mining
            SingleOutputStreamOperator<BasketCompletion> basketStream = shoppingEvents
                .keyBy(event -> event.sessionId)
                .process(new BasketTrackerFunction())
                .name("Track Shopping Baskets")
                .uid("basket-tracker");

            allPatterns = basketStream
                .keyBy(basket -> basket.userId != null ? basket.userId : basket.sessionId)
                .process(new PatternMinerFunction())
                .name("Mine Basket Patterns")
                .uid("pattern-miner");
        }

        // ========================================
        // STEP 4: Live Basket Tracking (for online learning)
        // ========================================

        LOG.info("\n🔧 LIVE PATTERN MINING: Session tracking + Association rules");
        LOG.info("   - Track active baskets with keyed state");
        LOG.info("   - Detect timeouts with timers (30 min)");
        LOG.info("   - Mine new patterns from completed baskets");

        // Track baskets
        SingleOutputStreamOperator<BasketCompletion> basketStream = shoppingEvents
            .keyBy(event -> event.sessionId)
            .process(new BasketTrackerFunction())
            .name("Track Shopping Baskets (Keyed State + Timers)")
            .uid("basket-tracker-live");

        // Mine new patterns
        DataStream<BasketPattern> liveMinedPatterns = basketStream
            .keyBy(basket -> basket.userId != null ? basket.userId : basket.sessionId)
            .process(new PatternMinerFunction())
            .name("Mine Live Basket Patterns")
            .uid("pattern-miner-live");

        // Union historical and live patterns
        DataStream<BasketPattern> combinedPatterns = allPatterns
            .union(liveMinedPatterns);

        // ========================================
        // STEP 5: Broadcast State - Distribute Patterns for Recommendations
        // ========================================

        LOG.info("\n📡 BROADCAST STATE: Pattern distribution for recommendations");
        LOG.info("   - Broadcast combined patterns to all tasks");
        LOG.info("   - Enable real-time inference with ML patterns");

        DataStream<RecommendationEvent> recommendations = shoppingEvents
            .keyBy(event -> event.sessionId)
            .connect(combinedPatterns.broadcast(PatternBroadcastState.PATTERNS_DESCRIPTOR))
            .process(new RecommendationGeneratorFunction())
            .name("Generate Recommendations (Broadcast State)")
            .uid("recommendation-generator");

        // ========================================
        // STEP 6: Sinks - Output to Kafka & Paimon
        // ========================================

        LOG.info("\n📤 Configuring Sinks");

        // Recommendations → Kafka
        setupRecommendationsSink(recommendations, config);

        // Live patterns → Kafka & Paimon (for continuous training data accumulation)
        setupPatternsSink(liveMinedPatterns, config, tEnv, paimonWarehouse);

        // WebSocket fanout for real-time UI
        setupWebSocketSink(recommendations, config);

        // ========================================
        // STEP 7: Execute Job
        // ========================================

        LOG.info("\n✅ Job configured successfully!");
        LOG.info("🎯 Pattern Summary:");
        LOG.info("   ✓ Hybrid Source: Historical (Paimon) + Live (Kafka)");
        LOG.info("   ✓ Broadcast State: Pattern distribution to all tasks");
        LOG.info("   ✓ Keyed State: Per-session basket tracking");
        LOG.info("   ✓ Timers: Session timeout detection");
        LOG.info("   ✓ Online Learning: Continuous pattern mining");
        LOG.info("\n🚀 Executing job with pre-training...\n");

        env.execute("Basket Analysis & Recommendation Job (WITH PRE-TRAINING)");
    }

    /**
     * JSON Event Parser (same as base job)
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
     * Setup Kafka & Paimon sinks for patterns (live-mined only)
     */
    private static void setupPatternsSink(
            DataStream<BasketPattern> patterns,
            BasketConfig config,
            StreamTableEnvironment tEnv,
            String paimonWarehouse) {

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

        // Paimon sink for accumulating training data
        if (paimonWarehouse != null && !paimonWarehouse.isEmpty()) {
            LOG.info("📦 Configuring Paimon sink for training data accumulation");
            PaimonSinkHelper.setupPaimonSink(patterns, paimonWarehouse, tEnv);
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
