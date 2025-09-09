package com.ververica.composable_job.flink.datagen;

import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.datastream.DataStream;

import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.base.DeliveryGuarantee;

import org.apache.flink.streaming.api.functions.source.SourceFunction;

import org.apache.flink.table.api.*;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import static org.apache.flink.table.api.Expressions.$;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

/**
 * Flink 1.20 implementation: KafkaSink + Paimon + custom SourceFunction.
 */
public class CoinbaseFlinkJob {

    public static void main(String[] args) throws Exception {
        // ------------------------------------------------------------------
        // Execution environment & parameters
        // ------------------------------------------------------------------
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        ParameterTool params = ParameterTool.fromArgs(args);
        env.getConfig().setGlobalJobParameters(params);

        // Read tickers from runtime parameters (comma‑separated list).
        String tickersParam = params.get("tickers",
                "BTC-USD,ETH-USD,DOGE-USD,XRP-USD,LTC-USD,BCH-USD,ADA-USD,SOL-USD,DOT-USD,LINK-USD,XLM-USD,UNI-USD,ALGO-USD,MATIC-USD");
        List<String> tickers = Arrays.asList(tickersParam.split(","));

        //------------------------------------------------------------------------
        // Custom WebSocket Source (implements SourceFunction, not RichSource)
        //------------------------------------------------------------------------
        DataStream<String> coinbaseStream = env.addSource(new CoinbaseWebSocketSource(tickers));

        //------------------------------------------------------------------------
        // Kafka Sink — unified sink API (since Flink 1.15)
        //------------------------------------------------------------------------
        KafkaSink<String> kafkaSink = KafkaSink.<String>builder()
                .setBootstrapServers("localhost:19092")
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.builder()
                                .setTopic("coinbase-ticker")
                                .setValueSerializationSchema(new SimpleStringSchema())
                                .build())
                // Exactly‑once needs transactional Kafka brokers; fallback to at‑least‑once here
                .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                .build();
        coinbaseStream.sinkTo(kafkaSink);

        //------------------------------------------------------------------------
        // Paimon Sink via Table API
        //------------------------------------------------------------------------
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        String paimonDDL = "CREATE TABLE coinbase_paimon (\n" +
                "  message STRING\n" +
                ") WITH (\n" +
                "  'connector' = 'paimon',\n" +
                "  'path' = 'file:///tmp/paimon/coinbase'\n" +
                ")";
        tableEnv.executeSql(paimonDDL);

        // Convert the DataStream into a Table.
        tableEnv.createTemporaryView("coinbase_stream", coinbaseStream, $("message"));

        // Insert data from the stream into the Paimon sink.
        tableEnv.executeSql("INSERT INTO coinbase_paimon SELECT message FROM coinbase_stream");

        // Execute the Flink job.
        env.execute("Coinbase Flink Job (Flink 1.20)");
    }

    /**
     * Custom WebSocket source using the legacy SourceFunction interface.
     * NOTE: SourceFunction will be removed in Flink 2.0, so migrate to the
     * FLIP‑27 Source API for long‑term compatibility.
     */
    public static class CoinbaseWebSocketSource implements SourceFunction<String> {

        private final List<String> tickers;
        private volatile boolean running = true;
        private transient WebSocketClient client;

        public CoinbaseWebSocketSource(List<String> tickers) {
            this.tickers = tickers;
        }

        @Override
        public void run(SourceContext<String> ctx) throws Exception {
            // Queue to decouple WebSocket thread from the source emission thread.
            LinkedBlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();

            // Build and connect WebSocket client.
            URI uri = new URI("wss://advanced-trade-ws.coinbase.com");
            client = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    try {
                        ObjectMapper objectMapper = new ObjectMapper();
                        Map<String, Object> subscribeMessage = new HashMap<>();
                        subscribeMessage.put("type", "subscribe");
                        subscribeMessage.put("product_ids", tickers);
                        subscribeMessage.put("channel", "ticker");
                        send(objectMapper.writeValueAsString(subscribeMessage));
                    } catch (Exception e) {
                        System.err.println("Subscription error: " + e.getMessage());
                    }
                }

                @Override
                public void onMessage(String message) {
                    messageQueue.offer(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("WebSocket closed: " + reason);
                }

                @Override
                public void onError(Exception ex) {
                    System.err.println("WebSocket error: " + ex.getMessage());
                }
            };

            client.connectBlocking(); // Wait until handshake completes

            try {
                while (running && !Thread.currentThread().isInterrupted()) {
                    String message = messageQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (message != null) {
                        synchronized (ctx.getCheckpointLock()) {
                            ctx.collect(message);
                        }
                    }
                }
            } finally {
                if (client != null) {
                    client.close();
                }
            }
        }

        @Override
        public void cancel() {
            running = false;
            if (client != null) {
                try {
                    client.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
