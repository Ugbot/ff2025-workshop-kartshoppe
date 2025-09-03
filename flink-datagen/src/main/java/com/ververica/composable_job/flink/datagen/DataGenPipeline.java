package com.ververica.composable_job.flink.datagen;

import com.ververica.composable_job.common.flink.KafkaUtils;
import com.ververica.composable_job.model.ProcessingEvent;
import com.ververica.composable_job.model.RandomNumberPoint;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.PipelineOptions;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.datagen.source.GeneratorFunction;
import org.apache.flink.formats.json.JsonSerializationSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

public class DataGenPipeline {

    public static void main(String[] args) throws Exception {
        String kafkaBrokers = ParameterTool.fromArgs(args).get("kafka-brokers", "localhost:9092");
        DataGenPipeline.create(kafkaBrokers, KafkaUtils.Topic.PROCESSING_FANOUT)
                .executeAsync();
    }

    public static StreamExecutionEnvironment create(String brokers, String outputTopic) {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.configure(createConfig());

        DataStream<RandomNumberPoint> sourceStream = createSourceStream(env, index -> RandomNumberPoint.generate());

        buildFlinkGraph(sourceStream)
                .sinkTo(KafkaUtils.Sink.create(brokers, outputTopic, new JsonSerializationSchema<>()))
                .uid("DataGenKafkaSink");

        return env;
    }

    private static Configuration createConfig() {
        Configuration config = new Configuration();
        config.set(PipelineOptions.GENERIC_TYPES, false);
        config.set(PipelineOptions.AUTO_GENERATE_UIDS, false);
        return config;
    }

    private static DataStream<RandomNumberPoint> createSourceStream(
            StreamExecutionEnvironment env,
            GeneratorFunction<Long, RandomNumberPoint> generator) {
        DataGeneratorSource<RandomNumberPoint> dataGenSource = new DataGeneratorSource<>(
                generator,
                Long.MAX_VALUE,
                RateLimiterStrategy.perSecond(5),
                TypeInformation.of(RandomNumberPoint.class));

        return env.fromSource(dataGenSource, WatermarkStrategy.noWatermarks(), "DataGenSource")
                .uid("DataGenSource")
                .setParallelism(1); // Due to a rate of 5 records per second parallelism 1 guarantees 1 record per 200ms
    }

    public static DataStream<ProcessingEvent<RandomNumberPoint>> buildFlinkGraph(DataStream<RandomNumberPoint> messages) {

        return messages
                .map(ProcessingEvent::of).uid("ToProcessingEventMapFunction");
    }
}
