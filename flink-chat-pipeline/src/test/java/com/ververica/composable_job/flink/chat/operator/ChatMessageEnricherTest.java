package com.ververica.composable_job.flink.chat.operator;

import com.ververica.composable_job.model.ChatMessage;
import com.ververica.composable_job.model.ChatMessageType;
import com.ververica.composable_job.model.ProcessingEvent;
import com.ververica.composable_job.model.RawChatMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ChatMessageEnricherTest {

    private static final ChatMessageEnricher ENRICHER = new ChatMessageEnricher(() -> "messageId1");

    @Test
    public void enrichIdTest() {

        RawChatMessage message = new RawChatMessage();

        ProcessingEvent<ChatMessage> actualMessage = ENRICHER.map(message);

        assertEquals("messageId1", actualMessage.payload.id);
    }

    @Test
    public void preserveFieldsTest() {
        RawChatMessage message = new RawChatMessage("user1", "user2", ChatMessageType.CHAT_MESSAGE, "Hello there!", 0L);

        ChatMessage actualMessage = ENRICHER.map(message).payload;

        assertEquals("user1", actualMessage.userId);
        assertEquals(ChatMessageType.CHAT_MESSAGE, actualMessage.type);
        assertEquals("Hello there!", actualMessage.message);
        assertEquals(0L, actualMessage.timestamp);
    }
}
