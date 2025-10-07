package com.ververica.composable_job.flink.assistant.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class AssistantResponse implements Serializable {
    public String messageId;
    public String sessionId;
    public String responseText;
    public List<String> recommendedProducts = new ArrayList<>();
    public long timestamp;

    public AssistantResponse() {}

    public AssistantResponse(String messageId, String sessionId, String responseText, long timestamp) {
        this.messageId = messageId;
        this.sessionId = sessionId;
        this.responseText = responseText;
        this.timestamp = timestamp;
    }
}
