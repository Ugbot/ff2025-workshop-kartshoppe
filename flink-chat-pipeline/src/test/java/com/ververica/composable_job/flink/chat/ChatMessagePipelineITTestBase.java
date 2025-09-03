package com.ververica.composable_job.flink.chat;

import com.ververica.composable_job.model.ChatMessage;
import com.ververica.composable_job.model.ProcessingEvent;
import com.ververica.composable_job.test.KafkaAdmin;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import java.util.UUID;

public class ChatMessagePipelineITTestBase {

    protected static final TypeReference<ProcessingEvent<ChatMessage>> CHAT_MESSAGE_TYPE_REF = new TypeReference<>() {
    };

    protected static String inputTopic;
    protected static String outputTopic;

    @Container
    protected static final ConfluentKafkaContainer KAFKA_CONTAINER =
            new ConfluentKafkaContainer("confluentinc/cp-kafka:7.7.2")
                    .withExposedPorts(9092)
                    .withNetwork(Network.newNetwork());

    protected static KafkaAdmin KAFKA_ADMIN;

    @BeforeAll
    protected static void beforeAll() {
        KAFKA_CONTAINER.start();
        KAFKA_ADMIN = new KafkaAdmin(KAFKA_CONTAINER.getBootstrapServers());
    }

    @BeforeEach
    protected void before() {
        inputTopic = "input_topic_" + UUID.randomUUID();
        outputTopic = "output_topic_" + UUID.randomUUID();

        KAFKA_ADMIN.createTopic(inputTopic);
        KAFKA_ADMIN.createTopic(outputTopic);
    }

}
