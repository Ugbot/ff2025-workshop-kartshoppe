package com.ververica.composable_job.flink.datagen;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.SourceFunction;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.Properties;

public class AISFlinkJob {

    // POJO for AIS message (using fields from AIS type 1/2/3 position report)
    public static class AisMessage {
        public int messageType;
        public int repeatIndicator;
        public long mmsi;
        public int navStatus;
        public int rateOfTurn;
        public int speedOverGround;
        public boolean positionAccuracy;
        public double longitude;  // in degrees
        public double latitude;   // in degrees
        public int courseOverGround;
        public int trueHeading;
        public int timeStamp;

        // No-arg constructor required for POJO
        public AisMessage() {}

        public AisMessage(int messageType, int repeatIndicator, long mmsi, int navStatus, int rateOfTurn,
                          int speedOverGround, boolean positionAccuracy, double longitude, double latitude,
                          int courseOverGround, int trueHeading, int timeStamp) {
            this.messageType = messageType;
            this.repeatIndicator = repeatIndicator;
            this.mmsi = mmsi;
            this.navStatus = navStatus;
            this.rateOfTurn = rateOfTurn;
            this.speedOverGround = speedOverGround;
            this.positionAccuracy = positionAccuracy;
            this.longitude = longitude;
            this.latitude = latitude;
            this.courseOverGround = courseOverGround;
            this.trueHeading = trueHeading;
            this.timeStamp = timeStamp;
        }

        @Override
        public String toString() {
            return "AisMessage{" +
                    "messageType=" + messageType +
                    ", repeatIndicator=" + repeatIndicator +
                    ", mmsi=" + mmsi +
                    ", navStatus=" + navStatus +
                    ", rateOfTurn=" + rateOfTurn +
                    ", speedOverGround=" + speedOverGround +
                    ", positionAccuracy=" + positionAccuracy +
                    ", longitude=" + longitude +
                    ", latitude=" + latitude +
                    ", courseOverGround=" + courseOverGround +
                    ", trueHeading=" + trueHeading +
                    ", timeStamp=" + timeStamp +
                    '}';
        }
    }

    // A custom source to read raw AIS payload strings from a TCP/IP socket.
    public static class AISDataSocketSource implements SourceFunction<String> {
        private final String host;
        private final int port;
        private volatile boolean isRunning = true;

        public AISDataSocketSource(String host, int port) {
            this.host = host;
            this.port = port;
        }

        @Override
        public void run(SourceContext<String> ctx) throws Exception {
            try (Socket socket = new Socket(host, port);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                String line;
                while (isRunning && (line = reader.readLine()) != null) {
                    // Assume each line is the AIS payload (e.g. from a NMEA sentence’s payload field)
                    ctx.collect(line);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void cancel() {
            isRunning = false;
        }
    }

    // Helper: Decodes a single AIS payload character into its 6-bit integer value.
    private static int decodeChar(char c) {
        int val = c - 48;
        if (val > 40) {
            val -= 8;
        }
        return val;
    }

    // Convert the AIS payload string into a binary string.
    private static String payloadToBinary(String payload) {
        StringBuilder binary = new StringBuilder();
        for (char c : payload.toCharArray()) {
            int val = decodeChar(c);
            // Convert to 6-bit binary string, padded with zeros.
            String bits = String.format("%6s", Integer.toBinaryString(val)).replace(' ', '0');
            binary.append(bits);
        }
        return binary.toString();
    }

    // Helper: Parse a binary substring as an integer (unsigned).
    private static int parseInt(String bits) {
        return Integer.parseInt(bits, 2);
    }

    // Helper: Parse a binary substring as a signed integer (two's complement).
    private static int parseSignedInt(String bits) {
        int value = Integer.parseInt(bits, 2);
        if (bits.charAt(0) == '1') {
            value -= (1 << bits.length());
        }
        return value;
    }

    // Parse the AIS payload (assumed to be AIS type 1/2/3) into an AisMessage POJO.
    public static AisMessage parseAisPayload(String payload) {
        try {
            String bin = payloadToBinary(payload);
            // Ensure that we have enough bits (should be at least 168 for type 1 messages)
            if (bin.length() < 168) {
                return null;
            }

            int messageType = parseInt(bin.substring(0, 6));
            int repeatIndicator = parseInt(bin.substring(6, 8));
            long mmsi = Long.parseLong(bin.substring(8, 38), 2);
            int navStatus = parseInt(bin.substring(38, 42));
            int rateOfTurn = parseSignedInt(bin.substring(42, 50));
            int speedOverGround = parseInt(bin.substring(50, 60));
            boolean posAcc = bin.charAt(60) == '1';
            // Longitude is a 28-bit signed integer; conversion: value / 600000.0 gives degrees.
            int lonRaw = parseSignedInt(bin.substring(61, 89));
            double longitude = lonRaw / 600000.0;
            // Latitude is a 27-bit signed integer; conversion: value / 600000.0 gives degrees.
            int latRaw = parseSignedInt(bin.substring(89, 116));
            double latitude = latRaw / 600000.0;
            int courseOverGround = parseInt(bin.substring(116, 128));
            int trueHeading = parseInt(bin.substring(128, 137));
            int timeStamp = parseInt(bin.substring(137, 143));

            return new AisMessage(messageType, repeatIndicator, mmsi, navStatus, rateOfTurn,
                    speedOverGround, posAcc, longitude, latitude, courseOverGround, trueHeading, timeStamp);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // MapFunction to convert raw AIS payload string to AisMessage POJO.
    public static class ParseAisPayload implements MapFunction<String, AisMessage> {
        @Override
        public AisMessage map(String value) throws Exception {
            return parseAisPayload(value);
        }
    }

    // MapFunction to convert AisMessage POJO to JSON using Jackson.
    public static class AisMessageToJson implements MapFunction<AisMessage, String> {
        private transient ObjectMapper mapper;

        @Override
        public String map(AisMessage value) throws Exception {
            if (mapper == null) {
                mapper = new ObjectMapper();
            }
            return mapper.writeValueAsString(value);
        }
    }

    public static void main(String[] args) throws Exception {
        // Set up the streaming execution environment.
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Create a stream from the AIS socket source.
        // Connect to the open AIS feed provided by Kystverket:
        // IP: 153.44.253.27, Port: 5631
        DataStream<String> rawAisStream = env.addSource(new AISDataSocketSource("153.44.253.27", 5631));

        // Parse the raw AIS payload into an AisMessage POJO.
        DataStream<AisMessage> parsedAisStream = rawAisStream
                .map(new ParseAisPayload())
                .filter(new FilterFunction<AisMessage>() {
                    @Override
                    public boolean filter(AisMessage value) throws Exception {
                        return value != null;
                    }
                });

        // Convert the POJO to JSON.
        DataStream<String> jsonAisStream = parsedAisStream.map(new AisMessageToJson());

        // Configure the Kafka sink using the new KafkaSink API.
        KafkaSink<String> kafkaSink = KafkaSink.<String>builder()
                .setBootstrapServers("localhost:9092")  // Adjust as needed
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.builder()
                                .setTopic("ais-shipping-data")
                                .setValueSerializationSchema(new SimpleStringSchema())
                                .build()
                )
                .build();

        // Add the Kafka sink to the stream.
        jsonAisStream.sinkTo(kafkaSink);

        // Execute the job.
        env.execute("Flink AIS Streaming Job - Structured AIS Payload to JSON (Flink 1.17)");
    }
}
