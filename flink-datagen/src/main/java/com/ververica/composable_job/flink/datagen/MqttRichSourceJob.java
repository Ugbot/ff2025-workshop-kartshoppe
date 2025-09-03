package com.ververica.composable_job.flink.datagen;


import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.flink.streaming.api.functions.source.RichSourceFunction;

import java.nio.charset.StandardCharsets;

public class MqttRichSourceJob{

    // POJO for VehiclePosition with Jackson annotations.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VehiclePosition {
        public String desi;       // e.g. "61"
        public String dir;        // e.g. "2"
        public Integer oper;      // e.g. 6
        public Integer veh;       // e.g. 284
        public String tst;        // e.g. "2025-02-18T08:30:55.973Z"
        public Integer tsi;       // e.g. 1739867455
        public Double spd;        // e.g. 0.70
        public Integer hdg;       // e.g. 187
        public Double lat;        // e.g. 60.201100
        @JsonProperty("long")
        public Double lng;        // JSON field "long" mapped to "lng"
        public Double acc;        // e.g. 0.01
        public Integer dl;        // e.g. -91
        public Integer odo;       // e.g. 11230
        public Integer drst;      // e.g. 0
        public String oday;       // e.g. "2025-02-18"
        public Integer jrn;       // e.g. 410
        public Integer line;      // e.g. 973
        public String start;      // e.g. "09:56"
        public String loc;        // e.g. "GPS"
        public Integer stop;      // e.g. 1173111
        public String route;      // e.g. "1061"
        public Integer occu;      // e.g. 0

        public VehiclePosition() {}
    }

    // POJO for DigitransitMessage wrapping the VehiclePosition.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DigitransitMessage {
        public VehiclePosition VP;
        public DigitransitMessage() {}

        // Utility to parse JSON into a DigitransitMessage using Jackson.
        public static DigitransitMessage fromJson(String json, ObjectMapper mapper) throws Exception {
            return mapper.readValue(json, DigitransitMessage.class);
        }
    }

    // A RichSourceFunction that uses the HiveMQ MQTT client (MQTT 5) for non-blocking operation.
    public static class MqttRichSource extends RichSourceFunction<DigitransitMessage> {
        private volatile boolean running = true;
        private transient Mqtt5AsyncClient client;
        private transient ObjectMapper objectMapper;

        @Override
        public void open(Configuration parameters) throws Exception {
            super.open(parameters);
            objectMapper = new ObjectMapper();
            client = MqttClient.builder()
                    .useMqttVersion5()
                    .identifier("flink-mqtt-client")
                    .serverHost("mqtt.hsl.fi")
                    .serverPort(1883)
                    .buildAsync();
            client.connect().join();
        }

        @Override
        public void run(SourceContext<DigitransitMessage> ctx) throws Exception {
            // Subscribe to the desired topic with a callback that emits messages downstream.
            client.subscribeWith()
                    .topicFilter("/hfp/v2/journey/ongoing/#")
                    .callback((Mqtt5Publish pub) -> {
                        String payload = new String(pub.getPayloadAsBytes(), StandardCharsets.UTF_8);
                        try {
                            DigitransitMessage dtMessage = DigitransitMessage.fromJson(payload, objectMapper);
                            synchronized (ctx.getCheckpointLock()) {
                                ctx.collect(dtMessage);
                            }
                            System.out.println("Received message: " + payload);
                        } catch (Exception e) {
                            System.err.println("Failed to parse message: " + payload + ", error: " + e.getMessage());
                        }
                    })
                    .send().join();

            // Keep the source running until cancellation.
            while (running) {
                Thread.sleep(1000);
            }
        }

        @Override
        public void cancel() {
            running = false;
            if (client != null) {
                client.disconnect().join();
            }
        }

        @Override
        public void close() throws Exception {
            if (client != null && client.getState().isConnected()) {
                client.disconnect().join();
            }
            super.close();
        }
    }

    public static void main(String[] args) throws Exception {
        // Set up the Flink streaming execution environment.
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // Add the modern MQTT RichSource to the job.
        DataStream<DigitransitMessage> mqttStream = env.addSource(new MqttRichSource())
                .name("ModernMqttRichSource");

        // For demonstration, print each received message.
        mqttStream.print();

        // Execute the job.
        env.execute("Flink MQTT Job with HiveMQ Modern Libs");
    }
}
