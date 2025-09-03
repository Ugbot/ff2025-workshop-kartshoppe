package com.ververica.composable_job.flink.chat;

import com.ververica.composable_job.model.ChatMessage;
import com.ververica.composable_job.model.ProcessingEvent;
import com.ververica.composable_job.model.RawChatMessage;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ChatMessagePipelineITTest extends ChatMessagePipelineITTestBase {

    @Test
    public void generateMessageIdTest() throws Exception {

        RawChatMessage message = new RawChatMessage();
        KAFKA_ADMIN.publishRecords(List.of(message), inputTopic);

        StreamExecutionEnvironment chatMessagePipelineEnv =
                ChatMessagePipeline.create(KAFKA_CONTAINER.getBootstrapServers(), inputTopic, outputTopic);
        chatMessagePipelineEnv.executeAsync();

        List<ProcessingEvent<ChatMessage>> values = KAFKA_ADMIN.getRecordValues(outputTopic, CHAT_MESSAGE_TYPE_REF);

        assertEquals(1, values.size());
        ProcessingEvent<ChatMessage> outMessage = values.get(0);
        assertEquals(ProcessingEvent.Type.CHAT_MESSAGE, outMessage.eventType);
        assertNotNull(outMessage.payload.id);
    }
}
