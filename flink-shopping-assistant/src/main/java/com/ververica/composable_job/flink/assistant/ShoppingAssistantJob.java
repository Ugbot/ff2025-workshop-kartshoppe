package com.ververica.composable_job.flink.assistant;

import com.ververica.composable_job.model.*;
import com.ververica.composable_job.model.ecommerce.*;
import com.ververica.composable_job.flink.chat.llm.LangChainAsyncFunction;
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
import org.apache.flink.streaming.api.datastream.AsyncDataStream;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.streaming.api.functions.async.AsyncRetryStrategy;
import org.apache.flink.streaming.api.functions.async.ResultFuture;
import org.apache.flink.streaming.api.functions.async.RichAsyncFunction;
import org.apache.flink.streaming.util.retryable.AsyncRetryStrategies;
import org.apache.flink.streaming.util.retryable.RetryPredicates;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Shopping Assistant Job that integrates AI chat with basket tracking
 * 
 * Features:
 * 1. Processes chat messages from users
 * 2. Maintains context of shopping basket
 * 3. Integrates with LLM for intelligent responses
 * 4. Provides personalized product recommendations
 * 5. Tracks conversation history per session
 */
public class ShoppingAssistantJob {
    private static final Logger LOG = LoggerFactory.getLogger(ShoppingAssistantJob.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    public static void main(String[] args) throws Exception {
        // Create execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);
        env.enableCheckpointing(30000);
        
        // Configuration
        String bootstrapServers = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:19092");
        String openAiApiKey = System.getenv().getOrDefault("OPENAI_API_KEY", "");
        String modelName = System.getenv().getOrDefault("MODEL_NAME", "gpt-4o-mini");
        
        LOG.info("Starting Shopping Assistant Job");
        LOG.info("Kafka bootstrap servers: {}", bootstrapServers);
        LOG.info("Using model: {}", modelName);
        
        // Input streams
        DataStream<ChatMessage> chatMessages = createChatStream(env, bootstrapServers);
        DataStream<BasketUpdate> basketUpdates = createBasketUpdateStream(env, bootstrapServers);
        DataStream<RecommendationEvent> recommendations = createRecommendationStream(env, bootstrapServers);
        
        // Process chat messages with context
        DataStream<EnrichedChatMessage> enrichedMessages = chatMessages
            .keyBy(msg -> msg.sessionId)
            .connect(basketUpdates.keyBy(update -> update.sessionId))
            .process(new ChatContextEnricher())
            .name("Enrich Chat with Context")
            .uid("chat-context-enricher");
        
        // Apply AI for intelligent responses
        DataStream<AssistantResponse> aiResponses = applyAI(
            enrichedMessages, 
            openAiApiKey, 
            modelName,
            recommendations
        );
        
        // Setup output sinks
        setupKafkaSinks(aiResponses, bootstrapServers);
        
        // Execute job
        env.execute("Shopping Assistant Job");
    }
    
    /**
     * Create chat message stream from Kafka
     */
    private static DataStream<ChatMessage> createChatStream(
            StreamExecutionEnvironment env,
            String bootstrapServers) {
        
        KafkaSource<String> source = KafkaSource.<String>builder()
            .setBootstrapServers(bootstrapServers)
            .setTopics("shopping-assistant-chat")
            .setGroupId("shopping-assistant")
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .build();
        
        return env.fromSource(
                source,
                WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofSeconds(1)),
                "Chat Messages Source"
            )
            .process(new ChatMessageParser());
    }
    
    /**
     * Create basket update stream
     */
    private static DataStream<BasketUpdate> createBasketUpdateStream(
            StreamExecutionEnvironment env,
            String bootstrapServers) {
        
        KafkaSource<String> source = KafkaSource.<String>builder()
            .setBootstrapServers(bootstrapServers)
            .setTopics("shopping-cart-events", "ecommerce-events")
            .setGroupId("shopping-assistant-basket")
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .build();
        
        return env.fromSource(
                source,
                WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofSeconds(1)),
                "Basket Updates Source"
            )
            .process(new BasketUpdateParser());
    }
    
    /**
     * Create recommendation stream for context
     */
    private static DataStream<RecommendationEvent> createRecommendationStream(
            StreamExecutionEnvironment env,
            String bootstrapServers) {
        
        KafkaSource<String> source = KafkaSource.<String>builder()
            .setBootstrapServers(bootstrapServers)
            .setTopics("product-recommendations")
            .setGroupId("shopping-assistant-recs")
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .build();
        
        return env.fromSource(
                source,
                WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofSeconds(1)),
                "Recommendations Source"
            )
            .process(new RecommendationParser());
    }
    
    /**
     * Apply AI processing to generate intelligent responses
     */
    private static DataStream<AssistantResponse> applyAI(
            DataStream<EnrichedChatMessage> messages,
            String apiKey,
            String modelName,
            DataStream<RecommendationEvent> recommendations) {
        
        // Enrich with recommendations
        DataStream<EnrichedChatMessage> withRecommendations = messages
            .keyBy(msg -> msg.sessionId)
            .connect(recommendations.keyBy(rec -> rec.sessionId))
            .process(new RecommendationEnricher())
            .name("Add Recommendations Context");
        
        // Create async retry strategy
        AsyncRetryStrategy retryStrategy = new AsyncRetryStrategies.FixedDelayRetryStrategyBuilder(3, 1000L)
            .ifResult(RetryPredicates.EMPTY_RESULT_PREDICATE)
            .ifException(RetryPredicates.HAS_EXCEPTION_PREDICATE)
            .build();
        
        // Apply LLM async function
        return AsyncDataStream.orderedWaitWithRetry(
            withRecommendations,
            new ShoppingAssistantLLM(apiKey, modelName),
            60,
            TimeUnit.SECONDS,
            100,
            retryStrategy
        ).name("Generate AI Response");
    }
    
    /**
     * Chat context enricher - maintains conversation history and basket state
     */
    public static class ChatContextEnricher 
            extends CoProcessFunction<ChatMessage, BasketUpdate, EnrichedChatMessage> {
        
        private ValueState<ConversationState> conversationState;
        private ValueState<BasketState> basketState;
        
        @Override
        public void open(Configuration parameters) throws Exception {
            conversationState = getRuntimeContext().getState(
                new ValueStateDescriptor<>("conversation", ConversationState.class));
            
            basketState = getRuntimeContext().getState(
                new ValueStateDescriptor<>("basket", BasketState.class));
        }
        
        @Override
        public void processElement1(ChatMessage message, Context ctx, Collector<EnrichedChatMessage> out) 
                throws Exception {
            
            ConversationState conversation = conversationState.value();
            if (conversation == null) {
                conversation = new ConversationState(message.sessionId);
            }
            
            BasketState basket = basketState.value();
            if (basket == null) {
                basket = new BasketState(message.sessionId);
            }
            
            // Create enriched message
            EnrichedChatMessage enriched = new EnrichedChatMessage();
            enriched.messageId = message.messageId;
            enriched.sessionId = message.sessionId;
            enriched.userId = message.userId;
            enriched.text = message.text;
            enriched.timestamp = message.timestamp;
            enriched.conversationHistory = conversation.getRecentMessages();
            enriched.basketContext = createBasketContext(basket);
            enriched.userIntent = detectIntent(message.text);
            
            // Update conversation history
            conversation.addMessage(message.text, "user");
            conversationState.update(conversation);
            
            out.collect(enriched);
            LOG.debug("Enriched chat message for session: {}", message.sessionId);
        }
        
        @Override
        public void processElement2(BasketUpdate update, Context ctx, Collector<EnrichedChatMessage> out) 
                throws Exception {
            
            BasketState basket = basketState.value();
            if (basket == null) {
                basket = new BasketState(update.sessionId);
            }
            
            // Update basket based on event
            switch (update.eventType) {
                case "ADD_TO_CART":
                    basket.addItem(update.product);
                    break;
                case "REMOVE_FROM_CART":
                    basket.removeItem(update.productId);
                    break;
                case "PURCHASE":
                    basket.clear();
                    break;
            }
            
            basketState.update(basket);
            LOG.debug("Updated basket for session: {}", update.sessionId);
        }
        
        private Map<String, Object> createBasketContext(BasketState basket) {
            Map<String, Object> context = new HashMap<>();
            context.put("itemCount", basket.items.size());
            context.put("totalValue", basket.getTotalValue());
            context.put("categories", basket.getCategories());
            context.put("items", basket.items.stream()
                .map(item -> item.name)
                .collect(Collectors.toList()));
            return context;
        }
        
        private String detectIntent(String text) {
            String lowerText = text.toLowerCase();
            
            if (lowerText.contains("recommend") || lowerText.contains("suggest")) {
                return "RECOMMENDATION";
            } else if (lowerText.contains("cart") || lowerText.contains("basket")) {
                return "CART_INQUIRY";
            } else if (lowerText.contains("price") || lowerText.contains("cost")) {
                return "PRICE_INQUIRY";
            } else if (lowerText.contains("find") || lowerText.contains("search")) {
                return "PRODUCT_SEARCH";
            } else if (lowerText.contains("help")) {
                return "HELP";
            }
            
            return "GENERAL";
        }
    }
    
    /**
     * LLM integration for shopping assistant
     */
    public static class ShoppingAssistantLLM extends RichAsyncFunction<EnrichedChatMessage, AssistantResponse> {
        
        private final String apiKey;
        private final String modelName;
        private transient LangChainAsyncFunction llmFunction;
        
        public ShoppingAssistantLLM(String apiKey, String modelName) {
            this.apiKey = apiKey;
            this.modelName = modelName;
        }
        
        @Override
        public void open(Configuration parameters) throws Exception {
            // Initialize LLM function (reusing existing infrastructure)
            llmFunction = new LangChainAsyncFunction(apiKey, modelName);
            llmFunction.open(parameters);
        }
        
        @Override
        public void asyncInvoke(EnrichedChatMessage message, ResultFuture<AssistantResponse> resultFuture) 
                throws Exception {
            
            // Build prompt with shopping context
            String prompt = buildShoppingPrompt(message);
            
            // Create a wrapper message for LLM
            EnrichedChatMessage llmMessage = new EnrichedChatMessage();
            llmMessage.text = prompt;
            llmMessage.messageId = message.messageId;
            
            // Use existing LLM infrastructure
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    List<EnrichedChatMessage> result = new ArrayList<>();
                    llmFunction.asyncInvoke(llmMessage, new ResultFuture<EnrichedChatMessage>() {
                        @Override
                        public void complete(Collection<EnrichedChatMessage> results) {
                            result.addAll(results);
                        }
                        
                        @Override
                        public void completeExceptionally(Throwable throwable) {
                            LOG.error("LLM failed", throwable);
                        }
                    });
                    
                    // Wait for result
                    Thread.sleep(1000); // Simple wait - in production use proper async
                    
                    if (!result.isEmpty()) {
                        return result.get(0).translatedText;
                    }
                } catch (Exception e) {
                    LOG.error("Failed to get LLM response", e);
                }
                return "I'm sorry, I'm having trouble understanding. Could you please rephrase?";
            });
            
            future.whenComplete((response, throwable) -> {
                if (throwable != null) {
                    resultFuture.completeExceptionally(throwable);
                } else {
                    AssistantResponse assistantResponse = new AssistantResponse();
                    assistantResponse.sessionId = message.sessionId;
                    assistantResponse.messageId = UUID.randomUUID().toString();
                    assistantResponse.responseText = response;
                    assistantResponse.timestamp = System.currentTimeMillis();
                    assistantResponse.recommendations = extractRecommendations(message);
                    
                    resultFuture.complete(Collections.singletonList(assistantResponse));
                }
            });
        }
        
        private String buildShoppingPrompt(EnrichedChatMessage message) {
            StringBuilder prompt = new StringBuilder();
            
            prompt.append("You are a helpful shopping assistant for an e-commerce store. ");
            prompt.append("Be friendly, concise, and helpful. Focus on helping the customer find products and make purchases.\n\n");
            
            // Add basket context
            if (message.basketContext != null && !message.basketContext.isEmpty()) {
                prompt.append("Current shopping cart:\n");
                prompt.append("- Items: ").append(message.basketContext.get("itemCount")).append("\n");
                prompt.append("- Total: $").append(message.basketContext.get("totalValue")).append("\n");
                prompt.append("- Products: ").append(message.basketContext.get("items")).append("\n\n");
            }
            
            // Add conversation history
            if (message.conversationHistory != null && !message.conversationHistory.isEmpty()) {
                prompt.append("Recent conversation:\n");
                for (String msg : message.conversationHistory) {
                    prompt.append(msg).append("\n");
                }
                prompt.append("\n");
            }
            
            // Add recommendations if available
            if (message.availableRecommendations != null && !message.availableRecommendations.isEmpty()) {
                prompt.append("Recommended products based on basket:\n");
                for (String rec : message.availableRecommendations) {
                    prompt.append("- ").append(rec).append("\n");
                }
                prompt.append("\n");
            }
            
            // Add user message
            prompt.append("Customer: ").append(message.text).append("\n");
            prompt.append("Assistant: ");
            
            return prompt.toString();
        }
        
        private List<ProductRecommendation> extractRecommendations(EnrichedChatMessage message) {
            List<ProductRecommendation> recommendations = new ArrayList<>();
            
            // Extract product recommendations based on context and intent
            if ("RECOMMENDATION".equals(message.userIntent) && 
                message.availableRecommendations != null) {
                
                for (String productId : message.availableRecommendations) {
                    ProductRecommendation rec = new ProductRecommendation();
                    rec.productId = productId;
                    rec.reason = "Based on your shopping basket";
                    rec.confidence = 0.8;
                    recommendations.add(rec);
                }
            }
            
            return recommendations;
        }
    }
    
    /**
     * Setup Kafka sinks for output
     */
    private static void setupKafkaSinks(DataStream<AssistantResponse> responses, String bootstrapServers) {
        // Assistant responses sink
        KafkaSink<String> responseSink = KafkaSink.<String>builder()
            .setBootstrapServers(bootstrapServers)
            .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                .setTopic("assistant-responses")
                .setValueSerializationSchema(new SimpleStringSchema())
                .build()
            )
            .build();
        
        responses
            .map(response -> MAPPER.writeValueAsString(response))
            .sinkTo(responseSink)
            .name("Assistant Responses Sink");
        
        // WebSocket fanout
        KafkaSink<String> websocketSink = KafkaSink.<String>builder()
            .setBootstrapServers(bootstrapServers)
            .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                .setTopic("websocket_fanout")
                .setValueSerializationSchema(new SimpleStringSchema())
                .build()
            )
            .build();
        
        responses
            .map(response -> {
                Map<String, Object> wsEvent = new HashMap<>();
                wsEvent.put("type", "ASSISTANT_RESPONSE");
                wsEvent.put("sessionId", response.sessionId);
                wsEvent.put("text", response.responseText);
                wsEvent.put("recommendedProducts", response.recommendations);
                wsEvent.put("timestamp", response.timestamp);
                return MAPPER.writeValueAsString(wsEvent);
            })
            .sinkTo(websocketSink)
            .name("WebSocket Fanout Sink");
    }
    
    // Data classes
    
    public static class ChatMessage implements Serializable {
        public String messageId;
        public String sessionId;
        public String userId;
        public String text;
        public long timestamp;
        public String type; // USER_MESSAGE, INIT, etc.
    }
    
    public static class BasketUpdate implements Serializable {
        public String sessionId;
        public String eventType;
        public String productId;
        public Product product;
        public long timestamp;
    }
    
    public static class EnrichedChatMessage extends EnrichedChatMessage {
        public List<String> conversationHistory;
        public Map<String, Object> basketContext;
        public String userIntent;
        public List<String> availableRecommendations;
    }
    
    public static class AssistantResponse implements Serializable {
        public String messageId;
        public String sessionId;
        public String responseText;
        public List<ProductRecommendation> recommendations;
        public long timestamp;
    }
    
    public static class ProductRecommendation implements Serializable {
        public String productId;
        public String productName;
        public double price;
        public String reason;
        public double confidence;
    }
    
    public static class ConversationState implements Serializable {
        public String sessionId;
        public List<String> messages = new ArrayList<>();
        public long lastActivity;
        
        public ConversationState() {}
        
        public ConversationState(String sessionId) {
            this.sessionId = sessionId;
            this.lastActivity = System.currentTimeMillis();
        }
        
        public void addMessage(String text, String sender) {
            messages.add(sender + ": " + text);
            if (messages.size() > 10) {
                messages.remove(0);
            }
            lastActivity = System.currentTimeMillis();
        }
        
        public List<String> getRecentMessages() {
            return new ArrayList<>(messages);
        }
    }
    
    public static class BasketState implements Serializable {
        public String sessionId;
        public List<Product> items = new ArrayList<>();
        
        public BasketState() {}
        
        public BasketState(String sessionId) {
            this.sessionId = sessionId;
        }
        
        public void addItem(Product product) {
            items.add(product);
        }
        
        public void removeItem(String productId) {
            items.removeIf(item -> item.productId.equals(productId));
        }
        
        public void clear() {
            items.clear();
        }
        
        public double getTotalValue() {
            return items.stream()
                .mapToDouble(item -> item.price)
                .sum();
        }
        
        public Set<String> getCategories() {
            return items.stream()
                .map(item -> item.category)
                .collect(Collectors.toSet());
        }
    }
    
    // Parser implementations
    
    public static class ChatMessageParser extends ProcessFunction<String, ChatMessage> {
        private static final ObjectMapper mapper = new ObjectMapper();
        
        @Override
        public void processElement(String value, Context ctx, Collector<ChatMessage> out) throws Exception {
            try {
                ChatMessage message = mapper.readValue(value, ChatMessage.class);
                out.collect(message);
            } catch (Exception e) {
                LOG.warn("Failed to parse chat message: {}", value, e);
            }
        }
    }
    
    public static class BasketUpdateParser extends ProcessFunction<String, BasketUpdate> {
        private static final ObjectMapper mapper = new ObjectMapper();
        
        @Override
        public void processElement(String value, Context ctx, Collector<BasketUpdate> out) throws Exception {
            try {
                // Parse as EcommerceEvent and convert
                EcommerceEvent event = mapper.readValue(value, EcommerceEvent.class);
                
                BasketUpdate update = new BasketUpdate();
                update.sessionId = event.sessionId;
                update.eventType = event.eventType;
                update.productId = event.productId;
                update.timestamp = System.currentTimeMillis();
                
                // Create product if adding to cart
                if ("ADD_TO_CART".equals(event.eventType)) {
                    Product product = new Product();
                    product.productId = event.productId;
                    product.name = event.productName;
                    product.price = event.price;
                    product.category = event.category;
                    update.product = product;
                }
                
                out.collect(update);
            } catch (Exception e) {
                LOG.warn("Failed to parse basket update: {}", value, e);
            }
        }
    }
    
    public static class RecommendationParser extends ProcessFunction<String, RecommendationEvent> {
        private static final ObjectMapper mapper = new ObjectMapper();
        
        @Override
        public void processElement(String value, Context ctx, Collector<RecommendationEvent> out) throws Exception {
            try {
                RecommendationEvent rec = mapper.readValue(value, RecommendationEvent.class);
                out.collect(rec);
            } catch (Exception e) {
                LOG.warn("Failed to parse recommendation: {}", value, e);
            }
        }
    }
    
    public static class RecommendationEvent implements Serializable {
        public String sessionId;
        public List<String> recommendedProducts;
        public String recommendationType;
    }
    
    public static class RecommendationEnricher 
            extends CoProcessFunction<EnrichedChatMessage, RecommendationEvent, EnrichedChatMessage> {
        
        private ValueState<List<String>> recommendationState;
        
        @Override
        public void open(Configuration parameters) throws Exception {
            recommendationState = getRuntimeContext().getState(
                new ValueStateDescriptor<>("recommendations", List.class));
        }
        
        @Override
        public void processElement1(EnrichedChatMessage message, Context ctx, Collector<EnrichedChatMessage> out) 
                throws Exception {
            
            List<String> recommendations = recommendationState.value();
            if (recommendations != null) {
                message.availableRecommendations = recommendations;
            }
            
            out.collect(message);
        }
        
        @Override
        public void processElement2(RecommendationEvent rec, Context ctx, Collector<EnrichedChatMessage> out) 
                throws Exception {
            recommendationState.update(rec.recommendedProducts);
        }
    }
}