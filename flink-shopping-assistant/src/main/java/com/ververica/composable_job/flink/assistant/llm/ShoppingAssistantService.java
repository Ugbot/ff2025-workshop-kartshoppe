package com.ververica.composable_job.flink.assistant.llm;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for interacting with LLM for shopping assistance
 */
public class ShoppingAssistantService implements Serializable {

    private static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();

    private final ChatLanguageModel model;

    public ShoppingAssistantService(String apiKey, String modelName) {
        this.model = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.7) // More creative for chat
                .build();
    }

    public CompletableFuture<String> generateResponse(String prompt) {
        return CompletableFuture.supplyAsync(
                () -> model.generate(prompt),
                EXECUTOR_SERVICE
        );
    }
}
