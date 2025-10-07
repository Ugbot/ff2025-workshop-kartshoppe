package com.ververica.composable_job.flink.assistant.llm;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for interacting with LLM for shopping assistance using LangChain4j
 *
 * KEY CONCEPTS:
 *
 * 1. LANGCHAIN4J ChatLanguageModel:
 *    - High-level abstraction over LLM providers (OpenAI, Anthropic, etc.)
 *    - Handles API calls, token counting, error handling
 *    - Simple interface: model.generate(prompt) -> response
 *
 * 2. ASYNC PATTERN:
 *    - Returns CompletableFuture for non-blocking execution
 *    - Compatible with Flink's Async I/O operators
 *    - Allows 100+ concurrent LLM calls without blocking threads
 *
 * 3. SERIALIZATION:
 *    - Implements Serializable for Flink's distributed execution
 *    - BUT: Model instance is NOT serialized (should be transient in caller)
 *    - Each Flink task initializes its own model instance
 *
 * ALTERNATIVE LLM PROVIDERS:
 * - Azure OpenAI: AzureOpenAiChatModel.builder()
 * - Anthropic Claude: AnthropicChatModel.builder()
 * - Google PaLM: GooglePalmChatModel.builder()
 * - Local (Ollama): OllamaChatModel.builder()
 * - Hugging Face: HuggingFaceChatModel.builder()
 */
public class ShoppingAssistantService implements Serializable {

    // Shared thread pool for async LLM calls
    // Note: CachedThreadPool scales based on load, reuses idle threads
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();

    private final ChatLanguageModel model;

    /**
     * Initialize the LangChain4j ChatLanguageModel
     *
     * @param apiKey OpenAI API key (get from https://platform.openai.com/api-keys)
     * @param modelName Model to use (e.g., "gpt-4o-mini", "gpt-4", "gpt-3.5-turbo")
     */
    public ShoppingAssistantService(String apiKey, String modelName) {
        this.model = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.7)  // Controls randomness: 0.0 = deterministic, 1.0 = creative
                // Additional optional configurations:
                // .maxTokens(500)           // Limit response length
                // .topP(0.9)                // Nucleus sampling parameter
                // .frequencyPenalty(0.0)    // Reduce repetition
                // .presencePenalty(0.0)     // Encourage topic diversity
                // .timeout(Duration.ofSeconds(60))  // Request timeout
                // .logRequests(true)        // Enable request logging
                // .logResponses(true)       // Enable response logging
                .build();
    }

    /**
     * Generate an async LLM response
     *
     * IMPORTANT: This method is non-blocking!
     * - The CompletableFuture is executed on EXECUTOR_SERVICE
     * - Flink thread returns immediately, not blocked during LLM call
     * - Result is delivered via callback when LLM responds
     *
     * @param prompt The input prompt (should include context, instructions, query)
     * @return CompletableFuture containing the LLM response text
     */
    public CompletableFuture<String> generateResponse(String prompt) {
        return CompletableFuture.supplyAsync(
                () -> model.generate(prompt),  // Calls OpenAI API
                EXECUTOR_SERVICE               // Executes on thread pool
        );
        // Flink's AsyncDataStream will handle this CompletableFuture:
        // - Registers callback via thenAccept()
        // - Completes ResultFuture when done
        // - Handles timeouts and errors
    }
}
