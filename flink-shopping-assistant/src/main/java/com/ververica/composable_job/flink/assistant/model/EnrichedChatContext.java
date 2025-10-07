package com.ververica.composable_job.flink.assistant.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EnrichedChatContext implements Serializable {
    public String messageId;
    public String sessionId;
    public String userId;
    public String text;
    public long timestamp;

    // Context information
    public List<String> conversationHistory = new ArrayList<>();
    public Map<String, Object> basketContext = new HashMap<>();
    public String userIntent;
    public List<String> availableRecommendations = new ArrayList<>();

    public EnrichedChatContext() {}

    public static EnrichedChatContext fromChatMessage(ChatMessage msg) {
        EnrichedChatContext context = new EnrichedChatContext();
        context.messageId = msg.messageId;
        context.sessionId = msg.sessionId;
        context.userId = msg.userId;
        context.text = msg.text;
        context.timestamp = msg.timestamp;
        return context;
    }
}
