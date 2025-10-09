package com.ververica.composable_job.flink.chat;

import com.ververica.composable_job.common.flink.KafkaUtils;
import com.ververica.composable_job.flink.chat.llm.LangChainAsyncFunction;
import com.ververica.composable_job.flink.chat.operator.ChatMessageEnricher;
import com.ververica.composable_job.flink.chat.operator.ProcessingEventMapper;
import com.ververica.composable_job.flink.chat.provider.SerializableSupplier;
import com.ververica.composable_job.flink.chat.typeinfo.EnrichedChatMessageInfoFactory;
import com.ververica.composable_job.flink.chat.typeinfo.ProcessingEventEnrichedChatMessageInfoFactory;
import com.ververica.composable_job.flink.chat.typeinfo.RawChatMessageInfoFactory;
import com.ververica.composable_job.model.EnrichedChatMessage;
import com.ververica.composable_job.model.ProcessingEvent;
import com.ververica.composable_job.model.RawChatMessage;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.PipelineOptions;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.formats.json.JsonDeserializationSchema;
import org.apache.flink.formats.json.JsonSerializationSchema;
import org.apache.flink.streaming.api.datastream.AsyncDataStream;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.async.AsyncRetryStrategy;
import org.apache.flink.streaming.util.retryable.AsyncRetryStrategies;
import org.apache.flink.streaming.util.retryable.RetryPredicates;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ChatMessagePipeline {

    public static final String INPUT_TOPIC = "websocket_fanout";

    public static void main(String[] args) throws Exception {
        String kafkaBrokers = ParameterTool.fromArgs(args).get("kafka-brokers", "localhost:19092");

        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = ParameterTool.fromArgs(args).get("api-key", "your-api-key-here");
        }

        String modelName = "gpt-4o-mini"; // ParameterTool.fromArgs(args).getRequired("translation.modelName");

        ChatMessagePipeline
                .create(
                        kafkaBrokers, INPUT_TOPIC, KafkaUtils.Topic.PROCESSING_FANOUT,
                        apiKey, modelName
                )
                .execute("ChatMessagePipeline");
    }

    // TODO fix tests
    public static StreamExecutionEnvironment create(
            String broker, String inputTopic, String outputTopic,
            String apiKey, String modelName) {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.configure(createConfig());

        env.enableCheckpointing(15_000);

        DataStream<RawChatMessage> messages = createSourceStream(env, broker, inputTopic);

        KafkaSink<ProcessingEvent<EnrichedChatMessage>> sink = KafkaUtils.Sink.create(broker, outputTopic, new JsonSerializationSchema<>());

        SerializableSupplier<String> idGenerator = () -> UUID.randomUUID().toString();

        SingleOutputStreamOperator<EnrichedChatMessage> notTranslatedStream = messages
                .map(new ChatMessageEnricher(idGenerator))
                .returns(EnrichedChatMessageInfoFactory.typeInfo())
                .uid(ChatMessageEnricher.UID);


        createTranslatedStream(notTranslatedStream, apiKey, modelName)
                .map(new ProcessingEventMapper())
                .returns(ProcessingEventEnrichedChatMessageInfoFactory.typeInfo())
                .uid(ProcessingEventMapper.UID)
                .sinkTo(sink)
                .uid("ChatKafkaSinkId");

        return env;
    }

    private static Configuration createConfig() {
        Configuration config = new Configuration();
        config.set(PipelineOptions.GENERIC_TYPES, false);
        config.set(PipelineOptions.AUTO_GENERATE_UIDS, false);
        return config;
    }

    private static DataStream<RawChatMessage> createSourceStream(StreamExecutionEnvironment env, String broker, String inputTopic) {
        KafkaSource<RawChatMessage> source = KafkaUtils.Source.create(
                broker,
                inputTopic,
                "chat-consumer-group",
                new JsonDeserializationSchema<>(RawChatMessage.class)
        );

        return env
                .fromSource(source, WatermarkStrategy.noWatermarks(), "ChatKafkaSource")
                .returns(RawChatMessageInfoFactory.typeInfo())
                .uid("ChatKafkaSourceId");
    }

    private static SingleOutputStreamOperator<EnrichedChatMessage> createTranslatedStream(
            final SingleOutputStreamOperator<EnrichedChatMessage> finalChatMemoryStream, String apiKey, String modelName) {

        // Async retry strategy with maxAttempts=3, fixedDelay=100ms
        final AsyncRetryStrategy<EnrichedChatMessage> asyncRetryStrategy =
                new AsyncRetryStrategies.FixedDelayRetryStrategyBuilder<EnrichedChatMessage>(3, 1000L)
                        .ifException(RetryPredicates.HAS_EXCEPTION_PREDICATE)
                        .build();

        return AsyncDataStream.orderedWaitWithRetry(
                        finalChatMemoryStream,
                        new LangChainAsyncFunction(apiKey, modelName), // TODO pass credentials here
                        10000,
                        TimeUnit.MILLISECONDS,
                        100,
                        asyncRetryStrategy)
                .returns(EnrichedChatMessageInfoFactory.typeInfo())
                .uid(LangChainAsyncFunction.UID)
                .name(LangChainAsyncFunction.UID);
    }
}
