package com.ververica.composable_job.flink.assistant.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ConversationState implements Serializable {
    public String sessionId;
    public List<String> messages = new ArrayList<>();
    public long lastActivity;

    public ConversationState() {}

    public ConversationState(String sessionId) {
        this.sessionId = sessionId;
        this.lastActivity = System.currentTimeMillis();
    }

    public void addMessage(String text, String sender) {
        messages.add(sender + ": " + text);
        // Keep only last 10 messages
        if (messages.size() > 10) {
            messages.remove(0);
        }
        lastActivity = System.currentTimeMillis();
    }

    public List<String> getRecentMessages() {
        return new ArrayList<>(messages);
    }
}
