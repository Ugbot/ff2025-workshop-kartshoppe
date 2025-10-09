package com.ververica.composable_job.flink.recommendations.patterns.ml_classification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.base.source.hybrid.HybridSource;
import org.apache.flink.connector.file.src.FileSource;
import org.apache.flink.connector.file.src.reader.TextLineInputFormat;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FLINK PATTERN: ML Classification with Hybrid Sources & Side Outputs
 *
 * PURPOSE:
 * Combines three powerful Flink patterns to create a production-ready ML inference pipeline:
 * 1. HYBRID SOURCES - Bootstrap from historical data (file), then switch to live stream (Kafka)
 * 2. ML CLASSIFICATION - Apply logistic regression for binary classification
 * 3. SIDE OUTPUTS - Route classified results to different processing pipelines
 *
 * USE CASE:
 * Classify purchase events as HIGH_VALUE vs STANDARD to enable:
 * - VIP treatment for high-value customers
 * - Targeted promotions and discounts
 * - Priority order processing
 * - Personalized recommendations
 *
 * KEY CONCEPTS:
 * - Logistic Regression: P(y=1|x) = σ(w₀ + w₁x₁ + w₂x₂ + ... + wₙxₙ)
 * - Feature Normalization: (x - μ) / σ for stable gradient descent
 * - Side Outputs: Efficient multi-way routing based on classification
 * - Hybrid Sources: Seamless transition from bounded to unbounded data
 *
 * WHEN TO USE:
 * - Need real-time ML inference on streaming data
 * - Want to bootstrap model state from historical data
 * - Require different processing for different classes
 * - Need explainable, fast predictions (logistic regression)
 *
 * ALTERNATIVE APPROACHES:
 * - External model serving (higher latency, more complex)
 * - Deep learning models (more accurate, slower inference)
 * - Rule-based classification (simpler, less accurate)
 */
public class MLClassificationExample {

    private static final Logger LOG = LoggerFactory.getLogger(MLClassificationExample.class);

    // Define side output tags
    public static final OutputTag<ClassifiedPurchase> HIGH_VALUE_TAG =
        new OutputTag<ClassifiedPurchase>("high-value") {};

    public static final OutputTag<ErrorEvent> PARSE_ERROR_TAG =
        new OutputTag<ErrorEvent>("parse-errors") {};

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);  // Adjust based on cluster

        String bootstrapServers = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:19092");
        String historicalFile = System.getenv().getOrDefault(
            "HISTORICAL_PURCHASES",
            "data/historical-purchases.jsonl"
        );

        LOG.info("🚀 Starting ML Classification Pipeline");
        LOG.info("📊 Historical data: {}", historicalFile);
        LOG.info("📡 Live stream: {}", bootstrapServers);

        // STEP 1: Create hybrid source (Historical → Live)
        HybridSource<String> source = createHybridSource(historicalFile, bootstrapServers);

        DataStream<String> purchaseStream = env.fromSource(
            source,
            WatermarkStrategy.noWatermarks(),
            "Purchase Hybrid Source",
            Types.STRING
        );

        // STEP 2: Apply ML classification with side outputs
        SingleOutputStreamOperator<ClassifiedPurchase> standardStream = purchaseStream
            .process(new MLClassificationProcessor())
            .name("ML Classification");

        // STEP 3: Extract side outputs
        DataStream<ClassifiedPurchase> highValueStream =
            standardStream.getSideOutput(HIGH_VALUE_TAG);

        DataStream<ErrorEvent> errorStream =
            standardStream.getSideOutput(PARSE_ERROR_TAG);

        // STEP 4: Process each stream independently

        // HIGH VALUE customers → VIP treatment
        highValueStream
            .map(cp -> {
                LOG.info("🌟 HIGH VALUE: User {} - ${:.2f} ({}% confidence)",
                        cp.purchase.userId,
                        cp.purchase.totalValue,
                        (int)(cp.probability * 100));
                return String.format("[VIP] User %s: $%.2f (%d%% confidence)",
                        cp.purchase.userId,
                        cp.purchase.totalValue,
                        (int)(cp.probability * 100));
            })
            .print("HIGH-VALUE");

        // STANDARD customers → normal processing
        standardStream
            .map(cp -> {
                LOG.info("✓ STANDARD: User {} - ${:.2f} ({}% confidence)",
                        cp.purchase.userId,
                        cp.purchase.totalValue,
                        (int)(cp.probability * 100));
                return String.format("[STANDARD] User %s: $%.2f (%d%% confidence)",
                        cp.purchase.userId,
                        cp.purchase.totalValue,
                        (int)(cp.probability * 100));
            })
            .print("STANDARD");

        // ERRORS → dead letter queue
        errorStream
            .map(e -> {
                LOG.error("❌ ERROR: {} - {}", e.errorType, e.errorMessage);
                return String.format("[ERROR] %s: %s", e.errorType, e.errorMessage);
            })
            .print("ERRORS");

        env.execute("ML Classification with Hybrid Sources & Side Outputs");
    }

    /**
     * Creates a Hybrid Source that:
     * 1. FIRST: Reads historical purchase data from file (bounded)
     * 2. THEN: Switches to live Kafka stream (unbounded)
     */
    private static HybridSource<String> createHybridSource(String filePath, String bootstrapServers) {
        // BOUNDED: Historical data from file
        FileSource<String> fileSource = FileSource
            .forRecordStreamFormat(
                new TextLineInputFormat(),
                new Path(filePath)
            )
            .build();

        // UNBOUNDED: Live purchase events from Kafka
        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
            .setBootstrapServers(bootstrapServers)
            .setTopics("purchase-events")
            .setGroupId("ml-classification-example")
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .setProperty("partition.discovery.interval.ms", "10000")
            .build();

        // Build hybrid source: file FIRST, then Kafka
        HybridSource<String> hybridSource = HybridSource.builder(fileSource)
            .addSource(kafkaSource)
            .build();

        LOG.info("✅ Hybrid Source created: {} (file) → {} (Kafka)", filePath, bootstrapServers);

        return hybridSource;
    }

    // ================================================================================
    // DATA MODELS
    // ================================================================================

    /**
     * Input: Purchase event from e-commerce system
     */
    public static class PurchaseEvent implements Serializable {
        public String userId;
        public String sessionId;
        public List<String> items;
        public double totalValue;
        public int basketSize;
        public int userPurchaseHistory;  // Number of previous purchases
        public long timestamp;

        public PurchaseEvent() {}
    }

    /**
     * Output: Purchase with ML classification and confidence score
     */
    public static class ClassifiedPurchase implements Serializable {
        public PurchaseEvent purchase;
        public String classification;  // "HIGH_VALUE" or "STANDARD"
        public double probability;     // 0.0 - 1.0
        public Map<String, Double> features;  // For explainability

        public ClassifiedPurchase() {}

        @Override
        public String toString() {
            return String.format("%s: User %s - $%.2f (%.2f%% confidence)",
                classification, purchase.userId, purchase.totalValue, probability * 100);
        }
    }

    /**
     * Side output: Parse errors and validation failures
     */
    public static class ErrorEvent implements Serializable {
        public String rawData;
        public String errorType;
        public String errorMessage;
        public long timestamp;

        public ErrorEvent() {}

        @Override
        public String toString() {
            return String.format("[%s] %s", errorType, errorMessage);
        }
    }

    // ================================================================================
    // ML CLASSIFIER
    // ================================================================================

    /**
     * Logistic Regression Classifier for binary classification
     *
     * Mathematical Model:
     * P(high_value) = σ(w₀ + w₁x₁ + w₂x₂ + w₃x₃)
     *
     * where:
     * - σ(z) = 1 / (1 + e^(-z))  [sigmoid function]
     * - x₁ = basket_size (normalized)
     * - x₂ = total_value (normalized)
     * - x₃ = user_purchase_history (normalized)
     * - w₀, w₁, w₂, w₃ = learned weights
     *
     * Features are normalized using z-score: (x - μ) / σ
     */
    public static class LogisticRegressionClassifier implements Serializable {

        private static final long serialVersionUID = 1L;

        // Model weights (learned from training data)
        // In production, load from model registry or state
        private final double[] weights;
        private final double bias;

        // Feature normalization parameters (computed from training data)
        private final double[] featureMeans;
        private final double[] featureStdDevs;

        /**
         * Initialize classifier with pre-trained weights
         *
         * Example weights for high-value purchase prediction:
         * - bias: -2.5 (baseline: most purchases are standard)
         * - weight[0]: 1.8 (basket_size: positive correlation)
         * - weight[1]: 3.2 (total_value: strong positive correlation)
         * - weight[2]: 0.9 (history: moderate positive correlation)
         */
        public LogisticRegressionClassifier() {
            // Pre-trained weights (in production, load from storage)
            this.weights = new double[] {
                1.8,   // basket_size weight
                3.2,   // total_value weight
                0.9    // user_history weight
            };
            this.bias = -2.5;

            // Normalization parameters from training data
            this.featureMeans = new double[] {
                3.0,    // avg basket size
                150.0,  // avg total value
                5.0     // avg user history
            };
            this.featureStdDevs = new double[] {
                2.0,    // basket size std dev
                100.0,  // total value std dev
                3.0     // user history std dev
            };
        }

        /**
         * Extract features from purchase event
         */
        public double[] extractFeatures(PurchaseEvent purchase) {
            return new double[] {
                purchase.basketSize,
                purchase.totalValue,
                purchase.userPurchaseHistory
            };
        }

        /**
         * Normalize features using z-score normalization
         * normalized[i] = (feature[i] - mean[i]) / stdDev[i]
         *
         * This ensures all features are on the same scale,
         * preventing features with large values from dominating.
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
         * Returns probability of being "HIGH_VALUE" (0.0 - 1.0)
         *
         * Steps:
         * 1. Extract features
         * 2. Normalize features
         * 3. Compute linear combination: z = bias + Σ(w_i * x_i)
         * 4. Apply sigmoid: P = 1 / (1 + e^(-z))
         */
        public double predict(PurchaseEvent purchase) {
            double[] features = extractFeatures(purchase);
            double[] normalized = normalizeFeatures(features);

            // Compute z = w₀ + w₁x₁ + w₂x₂ + ... + wₙxₙ
            double z = bias;
            for (int i = 0; i < weights.length; i++) {
                z += weights[i] * normalized[i];
            }

            // Apply sigmoid activation
            return sigmoid(z);
        }

        /**
         * Sigmoid activation function: σ(z) = 1 / (1 + e^(-z))
         * Maps any real number to range (0, 1)
         */
        private double sigmoid(double z) {
            return 1.0 / (1.0 + Math.exp(-z));
        }

        /**
         * Classify purchase based on probability threshold
         * Default threshold: 0.5 (balanced precision/recall)
         *
         * Adjust threshold based on business needs:
         * - Higher threshold (0.7): More precision, fewer VIPs
         * - Lower threshold (0.3): More recall, more VIPs
         */
        public String classify(PurchaseEvent purchase, double threshold) {
            double probability = predict(purchase);
            return probability >= threshold ? "HIGH_VALUE" : "STANDARD";
        }

        /**
         * Get feature importance for explainability
         */
        public Map<String, Double> getFeatureImportance() {
            Map<String, Double> importance = new HashMap<>();
            importance.put("basket_size", Math.abs(weights[0]));
            importance.put("total_value", Math.abs(weights[1]));
            importance.put("user_history", Math.abs(weights[2]));
            return importance;
        }
    }

    // ================================================================================
    // PROCESS FUNCTION
    // ================================================================================

    /**
     * ProcessFunction that applies ML classification and routes to side outputs
     *
     * Flow:
     * 1. Parse JSON purchase event
     * 2. Validate data (emit errors to side output)
     * 3. Extract features and make prediction
     * 4. Route based on classification:
     *    - HIGH_VALUE → side output for VIP treatment
     *    - STANDARD → main output for normal processing
     */
    public static class MLClassificationProcessor
            extends ProcessFunction<String, ClassifiedPurchase> {

        private transient LogisticRegressionClassifier classifier;
        private transient ObjectMapper objectMapper;

        private final double classificationThreshold = 0.5;  // Adjust based on business needs

        @Override
        public void open(Configuration parameters) {
            // Initialize classifier with pre-trained weights
            classifier = new LogisticRegressionClassifier();
            objectMapper = new ObjectMapper();

            LOG.info("✅ ML Classifier initialized");
            LOG.info("📊 Model weights: {}", java.util.Arrays.toString(classifier.weights));
            LOG.info("🎯 Classification threshold: {}", classificationThreshold);
        }

        @Override
        public void processElement(String value, Context ctx,
                                  Collector<ClassifiedPurchase> out) {
            try {
                // Parse purchase event
                PurchaseEvent purchase = objectMapper.readValue(value, PurchaseEvent.class);

                // Validate data
                if (!isValid(purchase)) {
                    emitError(ctx, value, "INVALID_DATA",
                            "Purchase has invalid values: totalValue=" + purchase.totalValue +
                            ", basketSize=" + purchase.basketSize);
                    return;
                }

                // Extract features and make prediction
                double probability = classifier.predict(purchase);
                String classification = classifier.classify(purchase, classificationThreshold);

                // Create classified result
                ClassifiedPurchase result = new ClassifiedPurchase();
                result.purchase = purchase;
                result.classification = classification;
                result.probability = probability;
                result.features = createFeatureMap(classifier.extractFeatures(purchase));

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
                emitError(ctx, value, "PARSE_ERROR", e.getMessage());
            }
        }

        /**
         * Validate purchase data
         */
        private boolean isValid(PurchaseEvent purchase) {
            return purchase != null &&
                   purchase.totalValue > 0 &&
                   purchase.basketSize > 0 &&
                   purchase.userId != null;
        }

        /**
         * Emit error to side output
         */
        private void emitError(Context ctx, String rawData, String errorType, String message) {
            ErrorEvent error = new ErrorEvent();
            error.rawData = rawData;
            error.errorType = errorType;
            error.errorMessage = message;
            error.timestamp = System.currentTimeMillis();

            ctx.output(PARSE_ERROR_TAG, error);
        }

        /**
         * Create feature map for explainability
         */
        private Map<String, Double> createFeatureMap(double[] features) {
            Map<String, Double> featureMap = new HashMap<>();
            featureMap.put("basket_size", features[0]);
            featureMap.put("total_value", features[1]);
            featureMap.put("user_history", features[2]);
            return featureMap;
        }
    }
}
