package com.ververica.composable_job.flink.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ververica.composable_job.common.flink.KafkaUtils;
import com.ververica.composable_job.flink.assistant.llm.ShoppingAssistantAsyncFunction;
import com.ververica.composable_job.flink.assistant.operator.ChatContextEnricher;
import com.ververica.composable_job.flink.assistant.model.*;
import com.ververica.composable_job.model.ProcessingEvent;
import com.ververica.composable_job.model.ecommerce.*;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.PipelineOptions;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.formats.json.JsonSerializationSchema;
import org.apache.flink.streaming.api.datastream.AsyncDataStream;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.streaming.api.functions.async.AsyncRetryStrategy;
import org.apache.flink.streaming.util.retryable.AsyncRetryStrategies;
import org.apache.flink.streaming.util.retryable.RetryPredicates;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

    public static final String CHAT_INPUT_TOPIC = "shopping-assistant-chat";
    public static final String BASKET_INPUT_TOPIC = "ecommerce-events";
    public static final String RECOMMENDATION_INPUT_TOPIC = "product-recommendations";
    public static final String OUTPUT_TOPIC = "assistant-responses";
    public static final String WEBSOCKET_OUTPUT_TOPIC = "websocket_fanout";

    public static void main(String[] args) throws Exception {
        String kafkaBrokers = ParameterTool.fromArgs(args).get("kafka-brokers", "localhost:19092");

        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = ParameterTool.fromArgs(args).get("api-key", "your-api-key-here");
        }

        String modelName = "gpt-4o-mini";

        LOG.info("Starting Shopping Assistant Job");
        LOG.info("Kafka bootstrap servers: {}", kafkaBrokers);
        LOG.info("Using model: {}", modelName);

        ShoppingAssistantJob
                .create(kafkaBrokers, apiKey, modelName)
                .execute("Shopping Assistant Job");
    }

    public static StreamExecutionEnvironment create(
            String broker, String apiKey, String modelName) {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.configure(createConfig());
        env.setParallelism(2);
        env.enableCheckpointing(30000);

        // Input streams
        DataStream<ChatMessage> chatMessages = createChatStream(env, broker);
        DataStream<EcommerceEvent> basketUpdates = createBasketUpdateStream(env, broker);
        DataStream<Recommendation> recommendations = createRecommendationStream(env, broker);

        // Enrich chat messages with context
        DataStream<EnrichedChatContext> enrichedMessages = chatMessages
            .keyBy(msg -> msg.sessionId)
            .connect(basketUpdates.keyBy(update -> update.sessionId))
            .process(new ChatContextEnricher())
            .name("Enrich Chat with Context")
            .uid("chat-context-enricher");

        // Apply AI for intelligent responses with retry strategy
        AsyncRetryStrategy<AssistantResponse> asyncRetryStrategy =
                new AsyncRetryStrategies.FixedDelayRetryStrategyBuilder<AssistantResponse>(3, 1000L)
                        .ifException(RetryPredicates.HAS_EXCEPTION_PREDICATE)
                        .build();

        SingleOutputStreamOperator<AssistantResponse> aiResponses = AsyncDataStream.orderedWaitWithRetry(
                enrichedMessages,
                new ShoppingAssistantAsyncFunction(apiKey, modelName),
                60,
                TimeUnit.SECONDS,
                100,
                asyncRetryStrategy)
            .name("Generate AI Response")
            .uid("shopping-assistant-llm");

        // Setup output sinks
        setupKafkaSinks(aiResponses, broker);

        return env;
    }

    private static Configuration createConfig() {
        Configuration config = new Configuration();
        config.set(PipelineOptions.GENERIC_TYPES, false);
        config.set(PipelineOptions.AUTO_GENERATE_UIDS, false);
        return config;
    }

    /**
     * Create chat message stream from Kafka
     */
    private static DataStream<ChatMessage> createChatStream(
            StreamExecutionEnvironment env,
            String bootstrapServers) {

        KafkaSource<String> source = KafkaSource.<String>builder()
            .setBootstrapServers(bootstrapServers)
            .setTopics(CHAT_INPUT_TOPIC)
            .setGroupId("shopping-assistant")
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .build();

        return env.fromSource(
                source,
                WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofSeconds(1)),
                "Chat Messages Source"
            )
            .process(new ChatMessageParser())
            .uid("chat-message-parser");
    }

    /**
     * Create basket update stream
     */
    private static DataStream<EcommerceEvent> createBasketUpdateStream(
            StreamExecutionEnvironment env,
            String bootstrapServers) {

        KafkaSource<String> source = KafkaSource.<String>builder()
            .setBootstrapServers(bootstrapServers)
            .setTopics(BASKET_INPUT_TOPIC)
            .setGroupId("shopping-assistant-basket")
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .build();

        return env.fromSource(
                source,
                WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofSeconds(1)),
                "Basket Updates Source"
            )
            .process(new BasketUpdateParser())
            .uid("basket-update-parser");
    }

    /**
     * Create recommendation stream for context
     */
    private static DataStream<Recommendation> createRecommendationStream(
            StreamExecutionEnvironment env,
            String bootstrapServers) {

        KafkaSource<String> source = KafkaSource.<String>builder()
            .setBootstrapServers(bootstrapServers)
            .setTopics(RECOMMENDATION_INPUT_TOPIC)
            .setGroupId("shopping-assistant-recs")
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .build();

        return env.fromSource(
                source,
                WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofSeconds(1)),
                "Recommendations Source"
            )
            .process(new RecommendationParser())
            .uid("recommendation-parser");
    }

    /**
     * Setup Kafka sinks for output
     */
    private static void setupKafkaSinks(DataStream<AssistantResponse> responses, String bootstrapServers) {
        // Assistant responses sink
        KafkaSink<String> responseSink = KafkaSink.<String>builder()
            .setBootstrapServers(bootstrapServers)
            .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                .setTopic(OUTPUT_TOPIC)
                .setValueSerializationSchema(new SimpleStringSchema())
                .build()
            )
            .build();

        responses
            .map(response -> {
                try {
                    return MAPPER.writeValueAsString(response);
                } catch (Exception e) {
                    LOG.error("Failed to serialize response", e);
                    return "{}";
                }
            })
            .sinkTo(responseSink)
            .name("Assistant Responses Sink")
            .uid("assistant-responses-sink");

        // WebSocket fanout
        KafkaSink<String> websocketSink = KafkaSink.<String>builder()
            .setBootstrapServers(bootstrapServers)
            .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                .setTopic(WEBSOCKET_OUTPUT_TOPIC)
                .setValueSerializationSchema(new SimpleStringSchema())
                .build()
            )
            .build();

        responses
            .map(response -> {
                try {
                    Map<String, Object> wsEvent = new HashMap<>();
                    wsEvent.put("type", "ASSISTANT_RESPONSE");
                    wsEvent.put("sessionId", response.sessionId);
                    wsEvent.put("text", response.responseText);
                    wsEvent.put("recommendedProducts", response.recommendedProducts);
                    wsEvent.put("timestamp", response.timestamp);
                    return MAPPER.writeValueAsString(wsEvent);
                } catch (Exception e) {
                    LOG.error("Failed to serialize websocket event", e);
                    return "{}";
                }
            })
            .sinkTo(websocketSink)
            .name("WebSocket Fanout Sink")
            .uid("websocket-fanout-sink");
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

    public static class BasketUpdateParser extends ProcessFunction<String, EcommerceEvent> {
        private static final ObjectMapper mapper = new ObjectMapper();

        @Override
        public void processElement(String value, Context ctx, Collector<EcommerceEvent> out) throws Exception {
            try {
                EcommerceEvent event = mapper.readValue(value, EcommerceEvent.class);
                out.collect(event);
            } catch (Exception e) {
                LOG.warn("Failed to parse basket update: {}", value, e);
            }
        }
    }

    public static class RecommendationParser extends ProcessFunction<String, Recommendation> {
        private static final ObjectMapper mapper = new ObjectMapper();

        @Override
        public void processElement(String value, Context ctx, Collector<Recommendation> out) throws Exception {
            try {
                Recommendation rec = mapper.readValue(value, Recommendation.class);
                out.collect(rec);
            } catch (Exception e) {
                LOG.warn("Failed to parse recommendation: {}", value, e);
            }
        }
    }
}
