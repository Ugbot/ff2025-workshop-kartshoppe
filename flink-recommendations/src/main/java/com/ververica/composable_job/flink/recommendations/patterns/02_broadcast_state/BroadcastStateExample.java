package com.ververica.composable_job.flink.recommendations.patterns.broadcast_state;

import com.ververica.composable_job.model.ecommerce.EcommerceEvent;
import org.apache.flink.api.common.state.BroadcastState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ReadOnlyBroadcastState;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.streaming.api.datastream.BroadcastStream;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.BroadcastProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FLINK PATTERN: Broadcast State
 *
 * PURPOSE:
 * Distribute low-throughput data (e.g., ML models, configurations, rules) to ALL parallel tasks
 * so they can enrich high-throughput data streams.
 *
 * KEY CONCEPTS:
 * 1. Broadcast stream = replicated to all parallel tasks
 * 2. Broadcast state = shared state available to all tasks
 * 3. Two streams: Main (high-throughput) + Broadcast (low-throughput)
 * 4. Read-only in main stream, read-write in broadcast stream
 *
 * WHEN TO USE:
 * - ML model distribution (this example!)
 * - Configuration updates (feature flags, rules)
 * - Dimension table enrichment (product catalog, user profiles)
 * - Dynamic pattern matching (fraud rules, alert conditions)
 *
 * VS OTHER PATTERNS:
 * - Keyed State: Per-key state (different value per key)
 * - Broadcast State: Same state replicated to ALL parallel tasks
 * - Side Inputs: Static data (loaded once at job start)
 * - Broadcast: Dynamic data (updated during job execution)
 *
 * REAL-WORLD EXAMPLE:
 * Product recommendation ML model updated hourly:
 *   - Main stream: User events (1000s/sec) → Need recommendations
 *   - Broadcast stream: ML model updates (1/hour) → Latest model
 *   - Each parallel task has latest model → Fast inference
 *
 * ARCHITECTURE:
 * <pre>
 *                         Broadcast Stream (Low Throughput)
 *                         ┌─────────────────────┐
 *                         │ ML Model Updates    │
 *                         │ (1 per hour)        │
 *                         └──────────┬──────────┘
 *                                    │
 *                         ┌──────────▼──────────┐
 *                         │   .broadcast()      │
 *                         └──────────┬──────────┘
 *                                    │
 *                    ┌───────────────┼───────────────┐
 *                    ▼               ▼               ▼
 *              ┌─────────┐     ┌─────────┐     ┌─────────┐
 *              │ Task 1  │     │ Task 2  │     │ Task 3  │
 *              │ Model v2│     │ Model v2│     │ Model v2│  ← All have same model
 *              └────▲────┘     └────▲────┘     └────▲────┘
 *                   │               │               │
 *    ┌──────────────┴───────────────┴───────────────┴─────┐
 *    │              Main Stream (High Throughput)          │
 *    │           User Events (1000s per second)            │
 *    └─────────────────────────────────────────────────────┘
 * </pre>
 */
public class BroadcastStateExample {

    private static final Logger LOG = LoggerFactory.getLogger(BroadcastStateExample.class);

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(3);  // 3 parallel tasks to show broadcast replication

        LOG.info("🤖 Starting Broadcast State Example");
        LOG.info("📊 Parallelism: 3 (all tasks will receive same model)");

        // ========================================
        // STREAM 1: High-throughput user events
        // ========================================

        DataStream<UserEvent> userEvents = createUserEventStream(env);

        // ========================================
        // STREAM 2: Low-throughput ML model updates
        // ========================================

        DataStream<MLModel> modelUpdates = createModelUpdateStream(env);

        // ========================================
        // PATTERN: Broadcast State
        // ========================================

        // Step 1: Define broadcast state descriptor
        // This state will be replicated to ALL parallel tasks
        MapStateDescriptor<String, MLModel> modelStateDescriptor = new MapStateDescriptor<>(
            "ml-model-state",
            Types.STRING,
            Types.POJO(MLModel.class)
        );

        // Step 2: Convert model stream to broadcast stream
        BroadcastStream<MLModel> broadcastStream = modelUpdates
            .broadcast(modelStateDescriptor);

        // Step 3: Connect main stream with broadcast stream
        DataStream<Recommendation> recommendations = userEvents
            .connect(broadcastStream)
            .process(new RecommendationEnricher(modelStateDescriptor))
            .name("Apply ML Model for Recommendations")
            .uid("recommendation-enricher");

        // Print results
        recommendations.print();

        env.execute("Broadcast State Pattern - ML Model Distribution");
    }

    /**
     * BroadcastProcessFunction:
     * - processElement(): Process main stream events (read-only broadcast state)
     * - processBroadcastElement(): Process broadcast stream events (read-write broadcast state)
     *
     * KEY RULE: Broadcast state is READ-ONLY in processElement(), READ-WRITE in processBroadcastElement()
     */
    public static class RecommendationEnricher
            extends BroadcastProcessFunction<UserEvent, MLModel, Recommendation> {

        private static final Logger LOG = LoggerFactory.getLogger(RecommendationEnricher.class);

        private final MapStateDescriptor<String, MLModel> modelStateDescriptor;

        public RecommendationEnricher(MapStateDescriptor<String, MLModel> modelStateDescriptor) {
            this.modelStateDescriptor = modelStateDescriptor;
        }

        /**
         * Process high-throughput user events.
         * USE BROADCAST STATE (read-only) to enrich events with ML predictions.
         */
        @Override
        public void processElement(
                UserEvent event,
                ReadOnlyContext ctx,
                Collector<Recommendation> out) throws Exception {

            // Get current ML model from broadcast state (READ-ONLY)
            ReadOnlyBroadcastState<String, MLModel> broadcastState =
                ctx.getBroadcastState(modelStateDescriptor);

            MLModel currentModel = broadcastState.get("current-model");

            if (currentModel == null) {
                LOG.warn("⚠️  No ML model loaded yet, skipping event for user: {}", event.userId);
                return;
            }

            // Apply ML model to generate recommendations
            List<String> recommendedProducts = currentModel.predict(event);

            Recommendation recommendation = new Recommendation();
            recommendation.userId = event.userId;
            recommendation.currentProduct = event.productId;
            recommendation.recommendedProducts = recommendedProducts;
            recommendation.modelVersion = currentModel.version;
            recommendation.modelAccuracy = currentModel.accuracy;
            recommendation.timestamp = System.currentTimeMillis();

            LOG.info("🎯 User {} viewing {} → Recommended {} (model v{})",
                event.userId, event.productId, recommendedProducts, currentModel.version);

            out.collect(recommendation);
        }

        /**
         * Process low-throughput ML model updates.
         * UPDATE BROADCAST STATE (read-write) so all parallel tasks get new model.
         */
        @Override
        public void processBroadcastElement(
                MLModel newModel,
                Context ctx,
                Collector<Recommendation> out) throws Exception {

            // Update broadcast state (READ-WRITE access here)
            BroadcastState<String, MLModel> broadcastState =
                ctx.getBroadcastState(modelStateDescriptor);

            MLModel oldModel = broadcastState.get("current-model");
            broadcastState.put("current-model", newModel);

            LOG.info("🔄 ML Model updated: v{} (accuracy: {:.2f}%) → v{} (accuracy: {:.2f}%) [Task {}]",
                oldModel != null ? oldModel.version : "none",
                oldModel != null ? oldModel.accuracy * 100 : 0.0,
                newModel.version,
                newModel.accuracy * 100,
                getRuntimeContext().getIndexOfThisSubtask());

            LOG.info("✅ Model v{} now active across ALL {} parallel tasks",
                newModel.version,
                getRuntimeContext().getNumberOfParallelSubtasks());
        }
    }

    /**
     * ML Model with simple prediction logic.
     * In production, this would load a TensorFlow/PyTorch model.
     */
    public static class MLModel implements Serializable {
        public int version;
        public double accuracy;
        public Map<String, List<String>> productRecommendations;  // productId → recommended products

        public MLModel() {
            this.productRecommendations = new HashMap<>();
        }

        public MLModel(int version, double accuracy) {
            this.version = version;
            this.accuracy = accuracy;
            this.productRecommendations = new HashMap<>();
        }

        /**
         * Predict recommended products based on current product.
         * Simplified logic - in production, use actual ML model.
         */
        public List<String> predict(UserEvent event) {
            // Use pre-computed recommendations from model
            return productRecommendations.getOrDefault(
                event.productId,
                List.of("DEFAULT_REC_001", "DEFAULT_REC_002", "DEFAULT_REC_003")
            );
        }

        public void addRecommendations(String productId, List<String> recommendations) {
            this.productRecommendations.put(productId, recommendations);
        }

        @Override
        public String toString() {
            return String.format("MLModel(v%d, accuracy=%.2f%%, products=%d)",
                version, accuracy * 100, productRecommendations.size());
        }
    }

    /**
     * User event requiring recommendation
     */
    public static class UserEvent implements Serializable {
        public String userId;
        public String productId;
        public String eventType;  // VIEW_PRODUCT, ADD_TO_CART, etc.
        public long timestamp;

        @Override
        public String toString() {
            return String.format("UserEvent[user=%s, product=%s, type=%s]",
                userId, productId, eventType);
        }
    }

    /**
     * Recommendation output with model metadata
     */
    public static class Recommendation implements Serializable {
        public String userId;
        public String currentProduct;
        public List<String> recommendedProducts;
        public int modelVersion;
        public double modelAccuracy;
        public long timestamp;

        @Override
        public String toString() {
            return String.format("[v%d, %.1f%%] User %s viewing %s → Recommended %s",
                modelVersion, modelAccuracy * 100, userId, currentProduct, recommendedProducts);
        }
    }

    // ========================================
    // Sample Data Generators
    // ========================================

    /**
     * Create sample user event stream (high throughput - 1000s/sec in production)
     */
    private static DataStream<UserEvent> createUserEventStream(StreamExecutionEnvironment env) {
        List<UserEvent> events = new ArrayList<>();

        // User 1: Looking at laptops
        events.add(createUserEvent("user-001", "LAPTOP_001", "VIEW_PRODUCT", 1000L));
        events.add(createUserEvent("user-001", "LAPTOP_002", "VIEW_PRODUCT", 2000L));

        // User 2: Looking at phones
        events.add(createUserEvent("user-002", "PHONE_001", "VIEW_PRODUCT", 1500L));
        events.add(createUserEvent("user-002", "PHONE_CASE_001", "VIEW_PRODUCT", 2500L));

        // User 3: Looking at headphones
        events.add(createUserEvent("user-003", "HEADPHONES_001", "VIEW_PRODUCT", 3000L));

        // More events after model update (simulated delay)
        events.add(createUserEvent("user-001", "LAPTOP_003", "VIEW_PRODUCT", 5000L));
        events.add(createUserEvent("user-002", "PHONE_002", "VIEW_PRODUCT", 5500L));

        return env.fromCollection(events);
    }

    /**
     * Create sample ML model update stream (low throughput - 1/hour in production)
     */
    private static DataStream<MLModel> createModelUpdateStream(StreamExecutionEnvironment env) {
        List<MLModel> models = new ArrayList<>();

        // Initial model (version 1)
        MLModel model1 = new MLModel(1, 0.85);  // 85% accuracy
        model1.addRecommendations("LAPTOP_001", List.of("MOUSE_001", "KEYBOARD_001", "MONITOR_001"));
        model1.addRecommendations("LAPTOP_002", List.of("LAPTOP_CASE_001", "USB_HUB_001", "LAPTOP_STAND_001"));
        model1.addRecommendations("PHONE_001", List.of("PHONE_CASE_001", "SCREEN_PROTECTOR_001", "CHARGER_001"));
        model1.addRecommendations("PHONE_CASE_001", List.of("PHONE_001", "PHONE_002", "PHONE_STAND_001"));
        model1.addRecommendations("HEADPHONES_001", List.of("HEADPHONE_CASE_001", "AMP_001", "CABLE_001"));
        models.add(model1);

        // Updated model (version 2) - better accuracy, different recommendations
        MLModel model2 = new MLModel(2, 0.92);  // 92% accuracy - improved!
        model2.addRecommendations("LAPTOP_001", List.of("LAPTOP_CASE_001", "EXTERNAL_SSD_001", "MOUSE_002"));
        model2.addRecommendations("LAPTOP_002", List.of("LAPTOP_STAND_001", "WEBCAM_001", "LAPTOP_COOLER_001"));
        model2.addRecommendations("LAPTOP_003", List.of("DOCKING_STATION_001", "MONITOR_002", "KEYBOARD_002"));
        model2.addRecommendations("PHONE_001", List.of("WIRELESS_CHARGER_001", "EARBUDS_001", "PHONE_CASE_002"));
        model2.addRecommendations("PHONE_002", List.of("PHONE_CASE_003", "CAR_MOUNT_001", "POWER_BANK_001"));
        model2.addRecommendations("HEADPHONES_001", List.of("DAC_001", "HEADPHONE_STAND_001", "REPLACEMENT_PADS_001"));
        models.add(model2);

        return env.fromCollection(models);
    }

    private static UserEvent createUserEvent(String userId, String productId, String eventType, long timestamp) {
        UserEvent event = new UserEvent();
        event.userId = userId;
        event.productId = productId;
        event.eventType = eventType;
        event.timestamp = timestamp;
        return event;
    }
}
