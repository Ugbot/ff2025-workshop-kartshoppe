package com.ververica.composable_job.flink.recommendations;

import com.ververica.composable_job.model.ecommerce.*;
import com.ververica.composable_job.flink.recommendations.ml.ProductRecommender;
import com.ververica.composable_job.flink.recommendations.ml.BasketPattern;
import com.ververica.composable_job.flink.recommendations.source.PaimonKafkaHybridSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.*;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.co.CoProcessFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.apache.paimon.flink.source.FlinkSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Advanced Basket Recommendation Job using ML with Hybrid Source
 * 
 * Features:
 * 1. Tracks user shopping baskets in real-time
 * 2. Uses DeepNetts logistic regression for next-item prediction
 * 3. Hybrid source: Paimon for historical training + Kafka for online learning
 * 4. Generates personalized recommendations based on basket patterns
 * 5. Implements collaborative filtering and association rules
 */
public class BasketRecommendationJob {
    private static final Logger LOG = LoggerFactory.getLogger(BasketRecommendationJob.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    public static void main(String[] args) throws Exception {
        // Create execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(4); // Increased for ML processing
        env.enableCheckpointing(60000); // 1-minute checkpoints for model state
        
        // Configuration
        String bootstrapServers = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:19092");
        String paimonWarehouse = System.getenv().getOrDefault("PAIMON_WAREHOUSE", "/tmp/paimon");
        
        LOG.info("Starting Basket Recommendation Job with ML");
        LOG.info("Kafka bootstrap servers: {}", bootstrapServers);
        LOG.info("Paimon warehouse: {}", paimonWarehouse);
        
        // Create hybrid source for training data
        DataStream<BasketPattern> trainingPatterns = createHybridTrainingSource(env, paimonWarehouse, bootstrapServers);
        
        // Create stream of shopping events
        DataStream<EcommerceEvent> shoppingEvents = createShoppingEventStream(env, bootstrapServers);
        
        // Track baskets and generate patterns
        KeyedStream<EcommerceEvent, String> keyedEvents = shoppingEvents
            .keyBy(event -> event.sessionId);
        
        // Process baskets and generate recommendations
        DataStream<RecommendationEvent> recommendations = keyedEvents
            .connect(trainingPatterns.broadcast())
            .process(new BasketRecommendationProcessor())
            .name("Basket Recommendation Processor")
            .uid("basket-recommender");
        
        // Window aggregation for pattern mining
        DataStream<BasketInsight> basketInsights = keyedEvents
            .window(TumblingProcessingTimeWindows.of(Time.minutes(5)))
            .process(new BasketPatternMiner())
            .name("Basket Pattern Miner")
            .uid("pattern-miner");
        
        // Setup sinks
        setupKafkaSinks(recommendations, basketInsights, bootstrapServers);
        
        // Execute job
        env.execute("Basket Recommendation Job with ML");
    }
    
    /**
     * Creates hybrid source combining Paimon historical data and Kafka streaming
     */
    private static DataStream<BasketPattern> createHybridTrainingSource(
            StreamExecutionEnvironment env, 
            String warehouse, 
            String bootstrapServers) {
        
        // For now, create from Kafka (Paimon integration would be added here)
        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
            .setBootstrapServers(bootstrapServers)
            .setTopics("basket-patterns")
            .setGroupId("recommendation-training")
            .setStartingOffsets(OffsetsInitializer.earliest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .build();
        
        return env.fromSource(
                kafkaSource,
                WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofSeconds(5)),
                "Training Patterns Source"
            )
            .flatMap(new FlatMapFunction<String, BasketPattern>() {
                @Override
                public void flatMap(String value, Collector<BasketPattern> out) {
                    try {
                        BasketPattern pattern = MAPPER.readValue(value, BasketPattern.class);
                        out.collect(pattern);
                    } catch (Exception e) {
                        LOG.error("Failed to parse basket pattern: {}", value, e);
                    }
                }
            });
    }
    
    /**
     * Creates stream of shopping events from Kafka
     */
    private static DataStream<EcommerceEvent> createShoppingEventStream(
            StreamExecutionEnvironment env,
            String bootstrapServers) {
        
        KafkaSource<String> source = KafkaSource.<String>builder()
            .setBootstrapServers(bootstrapServers)
            .setTopics("ecommerce-events")
            .setGroupId("basket-tracker")
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .build();
        
        return env.fromSource(
                source,
                WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofSeconds(3)),
                "Shopping Events Source"
            )
            .map(new MapFunction<String, EcommerceEvent>() {
                @Override
                public EcommerceEvent map(String value) throws Exception {
                    return MAPPER.readValue(value, EcommerceEvent.class);
                }
            })
            .filter(event -> event != null && event.sessionId != null);
    }
    
    /**
     * Main processor for basket tracking and recommendation generation
     */
    public static class BasketRecommendationProcessor 
            extends CoProcessFunction<EcommerceEvent, BasketPattern, RecommendationEvent> {
        
        private transient ValueState<BasketState> basketState;
        private transient ListState<String> recentProducts;
        private transient MapState<String, Integer> productFrequency;
        private transient ValueState<ProductRecommender> modelState;
        
        @Override
        public void open(Configuration parameters) throws Exception {
            // Initialize state
            basketState = getRuntimeContext().getState(
                new ValueStateDescriptor<>("basket-state", BasketState.class));
            
            recentProducts = getRuntimeContext().getListState(
                new ListStateDescriptor<>("recent-products", String.class));
            
            productFrequency = getRuntimeContext().getMapState(
                new MapStateDescriptor<>("product-frequency", String.class, Integer.class));
            
            modelState = getRuntimeContext().getState(
                new ValueStateDescriptor<>("ml-model", ProductRecommender.class));
            
            // Initialize ML model if not exists
            if (modelState.value() == null) {
                modelState.update(new ProductRecommender());
            }
        }
        
        @Override
        public void processElement1(EcommerceEvent event, Context ctx, Collector<RecommendationEvent> out) 
                throws Exception {
            
            BasketState basket = basketState.value();
            if (basket == null) {
                basket = new BasketState(event.sessionId, event.userId);
            }
            
            ProductRecommender recommender = modelState.value();
            
            // Process different event types
            switch (event.eventType) {
                case "ADD_TO_CART":
                    basket.addProduct(event.productId);
                    updateProductFrequency(event.productId);
                    
                    // Generate real-time recommendations
                    List<String> recommendations = recommender.recommendNextItems(
                        basket.getProducts(), 
                        getFrequentPatterns(), 
                        5
                    );
                    
                    emitRecommendation(event, recommendations, "CART_BASED", out);
                    break;
                    
                case "VIEW_PRODUCT":
                    basket.viewProduct(event.productId);
                    
                    // Context-aware recommendations
                    List<String> contextual = recommender.getContextualRecommendations(
                        event.productId,
                        basket.getViewHistory(),
                        3
                    );
                    
                    emitRecommendation(event, contextual, "VIEW_BASED", out);
                    break;
                    
                case "PURCHASE":
                    // Learn from completed purchase
                    recommender.updateModel(basket.getProducts(), event.productId);
                    basket.completePurchase();
                    
                    // Post-purchase recommendations
                    List<String> crossSell = recommender.getCrossSellItems(
                        basket.getPurchasedProducts(), 
                        5
                    );
                    
                    emitRecommendation(event, crossSell, "CROSS_SELL", out);
                    break;
                    
                case "REMOVE_FROM_CART":
                    basket.removeProduct(event.productId);
                    break;
            }
            
            // Update state
            basketState.update(basket);
            modelState.update(recommender);
            
            // Set timer for session timeout (30 minutes)
            ctx.timerService().registerProcessingTimeTimer(
                ctx.timestamp() + 30 * 60 * 1000
            );
        }
        
        @Override
        public void processElement2(BasketPattern pattern, Context ctx, Collector<RecommendationEvent> out) 
                throws Exception {
            // Update model with new training pattern
            ProductRecommender recommender = modelState.value();
            recommender.addTrainingPattern(pattern);
            modelState.update(recommender);
            
            LOG.debug("Updated model with pattern: {} -> {}", 
                pattern.getAntecedents(), pattern.getConsequent());
        }
        
        @Override
        public void onTimer(long timestamp, OnTimerContext ctx, Collector<RecommendationEvent> out) 
                throws Exception {
            // Session timeout - clear basket
            BasketState basket = basketState.value();
            if (basket != null && basket.isExpired(timestamp)) {
                LOG.info("Session expired for: {}", basket.sessionId);
                basketState.clear();
                recentProducts.clear();
            }
        }
        
        private void updateProductFrequency(String productId) throws Exception {
            Integer count = productFrequency.get(productId);
            productFrequency.put(productId, count == null ? 1 : count + 1);
        }
        
        private Map<String, Integer> getFrequentPatterns() throws Exception {
            Map<String, Integer> patterns = new HashMap<>();
            for (Map.Entry<String, Integer> entry : productFrequency.entries()) {
                if (entry.getValue() > 5) { // Threshold for frequent items
                    patterns.put(entry.getKey(), entry.getValue());
                }
            }
            return patterns;
        }
        
        private void emitRecommendation(
                EcommerceEvent event, 
                List<String> products, 
                String type,
                Collector<RecommendationEvent> out) {
            
            if (products != null && !products.isEmpty()) {
                RecommendationEvent rec = new RecommendationEvent();
                rec.sessionId = event.sessionId;
                rec.userId = event.userId;
                rec.recommendationType = type;
                rec.recommendedProducts = products;
                rec.confidence = 0.85; // Would be calculated by model
                rec.timestamp = System.currentTimeMillis();
                rec.triggerEvent = event.eventType;
                rec.context = new HashMap<>();
                rec.context.put("productId", event.productId);
                rec.context.put("category", event.category);
                
                out.collect(rec);
                LOG.debug("Generated {} recommendation for session {}: {}", 
                    type, event.sessionId, products);
            }
        }
    }
    
    /**
     * Window function for mining basket patterns
     */
    public static class BasketPatternMiner 
            extends ProcessWindowFunction<EcommerceEvent, BasketInsight, String, TimeWindow> {
        
        @Override
        public void process(
                String key, 
                Context context, 
                Iterable<EcommerceEvent> events, 
                Collector<BasketInsight> out) {
            
            BasketInsight insight = new BasketInsight();
            insight.sessionId = key;
            insight.windowStart = context.window().getStart();
            insight.windowEnd = context.window().getEnd();
            
            List<String> products = new ArrayList<>();
            Map<String, Integer> categoryCount = new HashMap<>();
            double totalValue = 0;
            
            for (EcommerceEvent event : events) {
                if (event.productId != null) {
                    products.add(event.productId);
                }
                if (event.category != null) {
                    categoryCount.merge(event.category, 1, Integer::sum);
                }
                if (event.price != null) {
                    totalValue += event.price;
                }
            }
            
            insight.products = products;
            insight.dominantCategory = categoryCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown");
            insight.basketValue = totalValue;
            insight.itemCount = products.size();
            
            // Calculate affinity scores
            insight.affinityScores = calculateAffinityScores(products);
            
            out.collect(insight);
        }
        
        private Map<String, Double> calculateAffinityScores(List<String> products) {
            Map<String, Double> scores = new HashMap<>();
            // Simple co-occurrence based affinity
            for (int i = 0; i < products.size(); i++) {
                for (int j = i + 1; j < products.size(); j++) {
                    String pair = products.get(i) + "-" + products.get(j);
                    scores.put(pair, 1.0);
                }
            }
            return scores;
        }
    }
    
    /**
     * Setup Kafka sinks for output
     */
    private static void setupKafkaSinks(
            DataStream<RecommendationEvent> recommendations,
            DataStream<BasketInsight> insights,
            String bootstrapServers) {
        
        // Recommendations sink
        KafkaSink<String> recommendationSink = KafkaSink.<String>builder()
            .setBootstrapServers(bootstrapServers)
            .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                .setTopic("product-recommendations")
                .setValueSerializationSchema(new SimpleStringSchema())
                .build()
            )
            .build();
        
        recommendations
            .map(rec -> MAPPER.writeValueAsString(rec))
            .sinkTo(recommendationSink)
            .name("Recommendations Sink");
        
        // Insights sink
        KafkaSink<String> insightSink = KafkaSink.<String>builder()
            .setBootstrapServers(bootstrapServers)
            .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                .setTopic("basket-insights")
                .setValueSerializationSchema(new SimpleStringSchema())
                .build()
            )
            .build();
        
        insights
            .map(insight -> MAPPER.writeValueAsString(insight))
            .sinkTo(insightSink)
            .name("Insights Sink");
        
        // WebSocket fanout sink
        KafkaSink<String> websocketSink = KafkaSink.<String>builder()
            .setBootstrapServers(bootstrapServers)
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
            .sinkTo(websocketSink)
            .name("WebSocket Fanout Sink");
    }
    
    /**
     * State class for tracking basket
     */
    public static class BasketState implements Serializable {
        public String sessionId;
        public String userId;
        public List<String> products = new ArrayList<>();
        public List<String> viewHistory = new ArrayList<>();
        public List<String> purchasedProducts = new ArrayList<>();
        public long lastActivity;
        
        public BasketState() {}
        
        public BasketState(String sessionId, String userId) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.lastActivity = System.currentTimeMillis();
        }
        
        public void addProduct(String productId) {
            products.add(productId);
            lastActivity = System.currentTimeMillis();
        }
        
        public void removeProduct(String productId) {
            products.remove(productId);
            lastActivity = System.currentTimeMillis();
        }
        
        public void viewProduct(String productId) {
            viewHistory.add(productId);
            if (viewHistory.size() > 20) {
                viewHistory.remove(0);
            }
            lastActivity = System.currentTimeMillis();
        }
        
        public void completePurchase() {
            purchasedProducts.addAll(products);
            products.clear();
            lastActivity = System.currentTimeMillis();
        }
        
        public boolean isExpired(long currentTime) {
            return currentTime - lastActivity > 30 * 60 * 1000; // 30 minutes
        }
        
        public List<String> getProducts() {
            return new ArrayList<>(products);
        }
        
        public List<String> getViewHistory() {
            return new ArrayList<>(viewHistory);
        }
        
        public List<String> getPurchasedProducts() {
            return new ArrayList<>(purchasedProducts);
        }
    }
    
    /**
     * Recommendation event
     */
    public static class RecommendationEvent implements Serializable {
        public String sessionId;
        public String userId;
        public String recommendationType;
        public List<String> recommendedProducts;
        public double confidence;
        public long timestamp;
        public String triggerEvent;
        public Map<String, String> context;
    }
    
    /**
     * Basket insight event
     */
    public static class BasketInsight implements Serializable {
        public String sessionId;
        public long windowStart;
        public long windowEnd;
        public List<String> products;
        public String dominantCategory;
        public double basketValue;
        public int itemCount;
        public Map<String, Double> affinityScores;
    }
}