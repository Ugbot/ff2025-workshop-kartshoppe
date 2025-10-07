package com.ververica.composable_job.flink.assistant.model;

import java.io.Serializable;

public class ChatMessage implements Serializable {
    public String messageId;
    public String sessionId;
    public String userId;
    public String text;
    public long timestamp;
    public String type; // USER_MESSAGE, INIT, etc.

    public ChatMessage() {}

    public ChatMessage(String messageId, String sessionId, String userId, String text, long timestamp) {
        this.messageId = messageId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.text = text;
        this.timestamp = timestamp;
        this.type = "USER_MESSAGE";
    }
}
