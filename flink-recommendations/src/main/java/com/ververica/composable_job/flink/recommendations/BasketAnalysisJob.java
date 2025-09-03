package com.ververica.composable_job.flink.recommendations;

import com.ververica.composable_job.model.ecommerce.*;
import com.ververica.composable_job.flink.recommendations.ml.ProductRecommender;
import com.ververica.composable_job.flink.recommendations.ml.BasketPattern;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.*;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.streaming.api.functions.co.CoProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.paimon.flink.sink.FlinkSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Basket Analysis and Recommendation Job
 * 
 * This job:
 * 1. Tracks shopping baskets from ecommerce events
 * 2. Mines patterns from completed baskets (purchases)
 * 3. Trains ML model on actual basket data
 * 4. Generates recommendations based on discovered patterns
 * 5. Stores patterns in Paimon for historical analysis
 */
public class BasketAnalysisJob {
    private static final Logger LOG = LoggerFactory.getLogger(BasketAnalysisJob.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    public static void main(String[] args) throws Exception {
        // Create execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(4);
        env.enableCheckpointing(30000); // 30-second checkpoints
        
        // Configuration
        String bootstrapServers = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:19092");
        String paimonWarehouse = System.getenv().getOrDefault("PAIMON_WAREHOUSE", "/tmp/paimon");
        
        LOG.info("Starting Basket Analysis and Recommendation Job");
        LOG.info("Kafka bootstrap servers: {}", bootstrapServers);
        LOG.info("Paimon warehouse: {}", paimonWarehouse);
        
        // Create stream of shopping events
        DataStream<EcommerceEvent> shoppingEvents = createShoppingEventStream(env, bootstrapServers);
        
        // Key by session for basket tracking
        KeyedStream<EcommerceEvent, String> keyedEvents = shoppingEvents
            .keyBy(event -> event.sessionId);
        
        // Process baskets and extract patterns
        DataStream<BasketCompletion> completedBaskets = keyedEvents
            .process(new BasketTracker())
            .name("Track Shopping Baskets")
            .uid("basket-tracker");
        
        // Mine patterns from completed baskets
        DataStream<BasketPattern> minedPatterns = completedBaskets
            .keyBy(basket -> basket.userId != null ? basket.userId : basket.sessionId)
            .process(new PatternMiner())
            .name("Mine Basket Patterns")
            .uid("pattern-miner");
        
        // Generate recommendations based on current baskets and learned patterns
        DataStream<RecommendationEvent> recommendations = keyedEvents
            .connect(minedPatterns.broadcast())
            .process(new RecommendationGenerator())
            .name("Generate Recommendations")
            .uid("recommendation-generator");
        
        // Setup sinks
        setupKafkaSinks(recommendations, minedPatterns, bootstrapServers);
        setupPaimonSink(minedPatterns, paimonWarehouse);
        
        // Execute job
        env.execute("Basket Analysis and Recommendation Job");
    }
    
    /**
     * Creates stream of shopping events from Kafka
     */
    private static DataStream<EcommerceEvent> createShoppingEventStream(
            StreamExecutionEnvironment env,
            String bootstrapServers) {
        
        KafkaSource<String> source = KafkaSource.<String>builder()
            .setBootstrapServers(bootstrapServers)
            .setTopics("ecommerce-events", "shopping-cart-events")
            .setGroupId("basket-analysis")
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .build();
        
        return env.fromSource(
                source,
                WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofSeconds(3)),
                "Shopping Events Source"
            )
            .process(new EventParser())
            .filter(event -> event != null && event.sessionId != null);
    }
    
    /**
     * Parse shopping events from JSON
     */
    public static class EventParser extends ProcessFunction<String, EcommerceEvent> {
        private static final ObjectMapper mapper = new ObjectMapper();
        
        @Override
        public void processElement(String value, Context ctx, Collector<EcommerceEvent> out) throws Exception {
            try {
                // Try to parse as ProcessingEvent wrapper first
                if (value.contains("\"eventType\"") && value.contains("\"payload\"")) {
                    Map<String, Object> wrapper = mapper.readValue(value, Map.class);
                    Map<String, Object> payload = (Map<String, Object>) wrapper.get("payload");
                    if (payload != null) {
                        EcommerceEvent event = mapper.convertValue(payload, EcommerceEvent.class);
                        out.collect(event);
                    }
                } else {
                    // Direct EcommerceEvent
                    EcommerceEvent event = mapper.readValue(value, EcommerceEvent.class);
                    out.collect(event);
                }
            } catch (Exception e) {
                LOG.warn("Failed to parse event: {}", value, e);
            }
        }
    }
    
    /**
     * Tracks shopping baskets and emits completed baskets
     */
    public static class BasketTracker extends KeyedProcessFunction<String, EcommerceEvent, BasketCompletion> {
        
        private ValueState<ActiveBasket> basketState;
        private ListState<BasketItem> itemsState;
        private ValueState<Long> lastActivityState;
        
        @Override
        public void open(Configuration parameters) throws Exception {
            basketState = getRuntimeContext().getState(
                new ValueStateDescriptor<>("active-basket", ActiveBasket.class));
            
            itemsState = getRuntimeContext().getListState(
                new ListStateDescriptor<>("basket-items", BasketItem.class));
            
            lastActivityState = getRuntimeContext().getState(
                new ValueStateDescriptor<>("last-activity", Long.class));
        }
        
        @Override
        public void processElement(EcommerceEvent event, Context ctx, Collector<BasketCompletion> out) 
                throws Exception {
            
            ActiveBasket basket = basketState.value();
            if (basket == null) {
                basket = new ActiveBasket(event.sessionId, event.userId);
            }
            
            // Update last activity
            lastActivityState.update(System.currentTimeMillis());
            
            // Process event
            switch (event.eventType) {
                case "ADD_TO_CART":
                    BasketItem item = new BasketItem(
                        event.productId,
                        event.productName,
                        event.category,
                        event.price,
                        1,
                        System.currentTimeMillis()
                    );
                    itemsState.add(item);
                    basket.itemCount++;
                    basket.totalValue += event.price != null ? event.price : 0;
                    LOG.debug("Added {} to basket {}", event.productId, event.sessionId);
                    break;
                    
                case "REMOVE_FROM_CART":
                    // Remove item from basket
                    List<BasketItem> items = new ArrayList<>();
                    itemsState.get().forEach(items::add);
                    items.removeIf(i -> i.productId.equals(event.productId));
                    itemsState.clear();
                    items.forEach(i -> {
                        try {
                            itemsState.add(i);
                        } catch (Exception e) {
                            LOG.error("Failed to update items", e);
                        }
                    });
                    basket.itemCount = items.size();
                    break;
                    
                case "PURCHASE":
                case "CHECKOUT":
                    // Basket completed - emit pattern
                    List<BasketItem> purchasedItems = new ArrayList<>();
                    itemsState.get().forEach(purchasedItems::add);
                    
                    if (!purchasedItems.isEmpty()) {
                        BasketCompletion completion = new BasketCompletion();
                        completion.sessionId = event.sessionId;
                        completion.userId = event.userId;
                        completion.items = purchasedItems;
                        completion.totalValue = basket.totalValue;
                        completion.completionTime = System.currentTimeMillis();
                        completion.itemCount = purchasedItems.size();
                        
                        out.collect(completion);
                        LOG.info("Basket completed: {} with {} items", event.sessionId, purchasedItems.size());
                        
                        // Clear basket after purchase
                        basketState.clear();
                        itemsState.clear();
                        lastActivityState.clear();
                        return;
                    }
                    break;
                    
                case "VIEW_PRODUCT":
                    basket.viewedProducts.add(event.productId);
                    if (basket.viewedProducts.size() > 50) {
                        basket.viewedProducts.remove(0);
                    }
                    break;
            }
            
            // Update basket state
            basketState.update(basket);
            
            // Set timer for session timeout (30 minutes)
            ctx.timerService().registerProcessingTimeTimer(
                System.currentTimeMillis() + 30 * 60 * 1000
            );
        }
        
        @Override
        public void onTimer(long timestamp, OnTimerContext ctx, Collector<BasketCompletion> out) 
                throws Exception {
            Long lastActivity = lastActivityState.value();
            if (lastActivity != null && timestamp - lastActivity > 30 * 60 * 1000) {
                // Session expired - could emit abandoned basket event here
                LOG.debug("Session expired for {}", ctx.getCurrentKey());
                basketState.clear();
                itemsState.clear();
                lastActivityState.clear();
            }
        }
    }
    
    /**
     * Mines patterns from completed baskets
     */
    public static class PatternMiner extends KeyedProcessFunction<String, BasketCompletion, BasketPattern> {
        
        private ListState<BasketCompletion> historicalBasketsState;
        private MapState<String, Integer> itemFrequencyState;
        private MapState<String, Map<String, Integer>> itemPairsState;
        private ValueState<Integer> basketCountState;
        
        @Override
        public void open(Configuration parameters) throws Exception {
            historicalBasketsState = getRuntimeContext().getListState(
                new ListStateDescriptor<>("historical-baskets", BasketCompletion.class));
            
            itemFrequencyState = getRuntimeContext().getMapState(
                new MapStateDescriptor<>("item-frequency", String.class, Integer.class));
            
            itemPairsState = getRuntimeContext().getMapState(
                new MapStateDescriptor<>("item-pairs", String.class, Map.class));
            
            basketCountState = getRuntimeContext().getState(
                new ValueStateDescriptor<>("basket-count", Integer.class, 0));
        }
        
        @Override
        public void processElement(BasketCompletion basket, Context ctx, Collector<BasketPattern> out) 
                throws Exception {
            
            // Store basket for analysis
            historicalBasketsState.add(basket);
            
            // Update basket count
            Integer count = basketCountState.value();
            basketCountState.update(count + 1);
            
            // Update item frequencies
            for (BasketItem item : basket.items) {
                Integer freq = itemFrequencyState.get(item.productId);
                itemFrequencyState.put(item.productId, freq == null ? 1 : freq + 1);
            }
            
            // Mine patterns from this basket
            List<String> productIds = basket.items.stream()
                .map(item -> item.productId)
                .collect(Collectors.toList());
            
            // Generate association rules for each item as consequent
            for (int i = 0; i < productIds.size(); i++) {
                String consequent = productIds.get(i);
                List<String> antecedents = new ArrayList<>();
                
                // Create antecedents from other items
                for (int j = 0; j < productIds.size(); j++) {
                    if (i != j) {
                        antecedents.add(productIds.get(j));
                    }
                }
                
                if (!antecedents.isEmpty()) {
                    // Calculate confidence based on historical data
                    double confidence = calculateConfidence(antecedents, consequent);
                    double support = calculateSupport(antecedents, consequent, count + 1);
                    
                    if (confidence > 0.3 && support > 0.01) {
                        BasketPattern pattern = new BasketPattern();
                        pattern.setAntecedents(antecedents);
                        pattern.setConsequent(consequent);
                        pattern.setConfidence(confidence);
                        pattern.setSupport(support);
                        pattern.setLift(calculateLift(confidence, consequent, count + 1));
                        pattern.setUserId(basket.userId);
                        pattern.setSessionId(basket.sessionId);
                        pattern.setTimestamp(System.currentTimeMillis());
                        
                        // Get category from first matching item
                        basket.items.stream()
                            .filter(item -> item.productId.equals(consequent))
                            .findFirst()
                            .ifPresent(item -> pattern.setCategory(item.category));
                        
                        out.collect(pattern);
                        LOG.debug("Mined pattern: {} -> {} (conf={}, sup={})", 
                            antecedents, consequent, confidence, support);
                    }
                    
                    // Also generate smaller patterns (pairs)
                    if (antecedents.size() > 1) {
                        for (String antecedent : antecedents) {
                            double pairConfidence = calculateConfidence(
                                Collections.singletonList(antecedent), consequent);
                            
                            if (pairConfidence > 0.4) {
                                BasketPattern pairPattern = new BasketPattern();
                                pairPattern.setAntecedents(Collections.singletonList(antecedent));
                                pairPattern.setConsequent(consequent);
                                pairPattern.setConfidence(pairConfidence);
                                pairPattern.setSupport(calculateSupport(
                                    Collections.singletonList(antecedent), consequent, count + 1));
                                pairPattern.setLift(calculateLift(pairConfidence, consequent, count + 1));
                                pairPattern.setTimestamp(System.currentTimeMillis());
                                
                                out.collect(pairPattern);
                            }
                        }
                    }
                }
            }
            
            // Periodically clean old baskets (keep last 1000)
            if (count > 1000) {
                List<BasketCompletion> allBaskets = new ArrayList<>();
                historicalBasketsState.get().forEach(allBaskets::add);
                
                if (allBaskets.size() > 1000) {
                    historicalBasketsState.clear();
                    allBaskets.stream()
                        .skip(allBaskets.size() - 1000)
                        .forEach(b -> {
                            try {
                                historicalBasketsState.add(b);
                            } catch (Exception e) {
                                LOG.error("Failed to update historical baskets", e);
                            }
                        });
                }
            }
        }
        
        private double calculateConfidence(List<String> antecedents, String consequent) throws Exception {
            // Simple confidence calculation based on co-occurrence
            // In production, would use more sophisticated methods
            int coOccurrence = 0;
            int antecedentOccurrence = 0;
            
            for (BasketCompletion basket : historicalBasketsState.get()) {
                Set<String> basketProducts = basket.items.stream()
                    .map(item -> item.productId)
                    .collect(Collectors.toSet());
                
                if (basketProducts.containsAll(antecedents)) {
                    antecedentOccurrence++;
                    if (basketProducts.contains(consequent)) {
                        coOccurrence++;
                    }
                }
            }
            
            if (antecedentOccurrence == 0) {
                // First time seeing this combination
                return 0.5; // Default confidence
            }
            
            return (double) coOccurrence / antecedentOccurrence;
        }
        
        private double calculateSupport(List<String> antecedents, String consequent, int totalBaskets) 
                throws Exception {
            if (totalBaskets == 0) return 0;
            
            int occurrence = 0;
            for (BasketCompletion basket : historicalBasketsState.get()) {
                Set<String> basketProducts = basket.items.stream()
                    .map(item -> item.productId)
                    .collect(Collectors.toSet());
                
                if (basketProducts.containsAll(antecedents) && basketProducts.contains(consequent)) {
                    occurrence++;
                }
            }
            
            return (double) occurrence / totalBaskets;
        }
        
        private double calculateLift(double confidence, String consequent, int totalBaskets) 
                throws Exception {
            Integer consequentFreq = itemFrequencyState.get(consequent);
            if (consequentFreq == null || totalBaskets == 0) {
                return 1.0;
            }
            
            double consequentProb = (double) consequentFreq / totalBaskets;
            if (consequentProb == 0) {
                return 1.0;
            }
            
            return confidence / consequentProb;
        }
    }
    
    /**
     * Generates recommendations based on current basket and learned patterns
     */
    public static class RecommendationGenerator 
            extends CoProcessFunction<EcommerceEvent, BasketPattern, RecommendationEvent> {
        
        private ValueState<CurrentBasket> basketState;
        private ListState<BasketPattern> patternsState;
        private transient ProductRecommender recommender;
        
        @Override
        public void open(Configuration parameters) throws Exception {
            basketState = getRuntimeContext().getState(
                new ValueStateDescriptor<>("current-basket", CurrentBasket.class));
            
            patternsState = getRuntimeContext().getListState(
                new ListStateDescriptor<>("learned-patterns", BasketPattern.class));
            
            recommender = new ProductRecommender();
        }
        
        @Override
        public void processElement1(EcommerceEvent event, Context ctx, Collector<RecommendationEvent> out) 
                throws Exception {
            
            CurrentBasket basket = basketState.value();
            if (basket == null) {
                basket = new CurrentBasket(event.sessionId, event.userId);
            }
            
            // Update basket based on event
            switch (event.eventType) {
                case "ADD_TO_CART":
                    basket.products.add(event.productId);
                    
                    // Generate recommendations based on current basket
                    List<String> recommendations = generateRecommendations(basket.products);
                    
                    if (!recommendations.isEmpty()) {
                        RecommendationEvent rec = new RecommendationEvent();
                        rec.sessionId = event.sessionId;
                        rec.userId = event.userId;
                        rec.recommendationType = "BASKET_BASED";
                        rec.recommendedProducts = recommendations;
                        rec.confidence = 0.75; // Would be calculated
                        rec.timestamp = System.currentTimeMillis();
                        rec.triggerEvent = "ADD_TO_CART";
                        rec.context = new HashMap<>();
                        rec.context.put("basketSize", String.valueOf(basket.products.size()));
                        
                        out.collect(rec);
                        LOG.debug("Generated recommendations for {}: {}", event.sessionId, recommendations);
                    }
                    break;
                    
                case "VIEW_PRODUCT":
                    basket.viewHistory.add(event.productId);
                    
                    // Context-based recommendations
                    List<String> viewRecs = generateViewRecommendations(event.productId);
                    if (!viewRecs.isEmpty()) {
                        RecommendationEvent rec = new RecommendationEvent();
                        rec.sessionId = event.sessionId;
                        rec.userId = event.userId;
                        rec.recommendationType = "VIEW_BASED";
                        rec.recommendedProducts = viewRecs;
                        rec.confidence = 0.65;
                        rec.timestamp = System.currentTimeMillis();
                        rec.triggerEvent = "VIEW_PRODUCT";
                        rec.context = new HashMap<>();
                        rec.context.put("viewedProduct", event.productId);
                        
                        out.collect(rec);
                    }
                    break;
                    
                case "PURCHASE":
                    // Clear basket after purchase
                    basketState.clear();
                    return;
            }
            
            basketState.update(basket);
        }
        
        @Override
        public void processElement2(BasketPattern pattern, Context ctx, Collector<RecommendationEvent> out) 
                throws Exception {
            // Store new pattern
            patternsState.add(pattern);
            
            // Update recommender model
            recommender.addTrainingPattern(pattern);
            
            // Periodically clean old patterns (keep last 5000)
            List<BasketPattern> allPatterns = new ArrayList<>();
            patternsState.get().forEach(allPatterns::add);
            
            if (allPatterns.size() > 5000) {
                patternsState.clear();
                allPatterns.stream()
                    .skip(allPatterns.size() - 5000)
                    .forEach(p -> {
                        try {
                            patternsState.add(p);
                        } catch (Exception e) {
                            LOG.error("Failed to update patterns", e);
                        }
                    });
            }
        }
        
        private List<String> generateRecommendations(List<String> currentProducts) throws Exception {
            Set<String> recommendations = new HashSet<>();
            Set<String> currentSet = new HashSet<>(currentProducts);
            
            // Find matching patterns
            for (BasketPattern pattern : patternsState.get()) {
                if (currentSet.containsAll(pattern.getAntecedents()) && 
                    !currentSet.contains(pattern.getConsequent())) {
                    
                    if (pattern.getConfidence() > 0.5) {
                        recommendations.add(pattern.getConsequent());
                    }
                }
            }
            
            // Limit to top 5
            return recommendations.stream()
                .limit(5)
                .collect(Collectors.toList());
        }
        
        private List<String> generateViewRecommendations(String productId) throws Exception {
            Set<String> recommendations = new HashSet<>();
            
            // Find patterns where this product is an antecedent
            for (BasketPattern pattern : patternsState.get()) {
                if (pattern.getAntecedents().contains(productId)) {
                    recommendations.add(pattern.getConsequent());
                }
            }
            
            return recommendations.stream()
                .limit(3)
                .collect(Collectors.toList());
        }
    }
    
    /**
     * Setup Kafka sinks
     */
    private static void setupKafkaSinks(
            DataStream<RecommendationEvent> recommendations,
            DataStream<BasketPattern> patterns,
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
        
        // Patterns sink (for monitoring)
        KafkaSink<String> patternSink = KafkaSink.<String>builder()
            .setBootstrapServers(bootstrapServers)
            .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                .setTopic("basket-patterns")
                .setValueSerializationSchema(new SimpleStringSchema())
                .build()
            )
            .build();
        
        patterns
            .map(pattern -> MAPPER.writeValueAsString(pattern))
            .sinkTo(patternSink)
            .name("Patterns Sink");
        
        // WebSocket sink
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
            .name("WebSocket Sink");
    }
    
    /**
     * Setup Paimon sink for pattern storage
     */
    private static void setupPaimonSink(DataStream<BasketPattern> patterns, String warehouse) {
        // Paimon sink would be configured here
        // For now, patterns are stored in Kafka
        LOG.info("Paimon sink configured for warehouse: {}", warehouse);
    }
    
    // Data classes
    
    public static class ActiveBasket implements Serializable {a d
        public String sessionId;
        public String userId;
        public List<String> viewedProducts = new ArrayList<>();
        public int itemCount = 0;
        public double totalValue = 0;
        public long createdAt;
        
        public ActiveBasket() {}
        
        public ActiveBasket(String sessionId, String userId) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.createdAt = System.currentTimeMillis();
        }
    }
    
    public static class CurrentBasket implements Serializable {
        public String sessionId;
        public String userId;
        public List<String> products = new ArrayList<>();
        public List<String> viewHistory = new ArrayList<>();
        
        public CurrentBasket() {}
        
        public CurrentBasket(String sessionId, String userId) {
            this.sessionId = sessionId;
            this.userId = userId;
        }
    }
    
    public static class BasketItem implements Serializable {
        public String productId;
        public String productName;
        public String category;
        public Double price;
        public int quantity;
        public long addedAt;
        
        public BasketItem() {}
        
        public BasketItem(String productId, String productName, String category, 
                         Double price, int quantity, long addedAt) {
            this.productId = productId;
            this.productName = productName;
            this.category = category;
            this.price = price;
            this.quantity = quantity;
            this.addedAt = addedAt;
        }
    }
    
    public static class BasketCompletion implements Serializable {
        public String sessionId;
        public String userId;
        public List<BasketItem> items;
        public double totalValue;
        public long completionTime;
        public int itemCount;
    }
    
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
}