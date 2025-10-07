package com.ververica.composable_job.flink.assistant.llm;

import com.ververica.composable_job.flink.assistant.model.AssistantResponse;
import com.ververica.composable_job.flink.assistant.model.EnrichedChatContext;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.async.ResultFuture;
import org.apache.flink.streaming.api.functions.async.RichAsyncFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Flink Async Function that integrates LangChain4j for LLM-powered chat responses
 *
 * ARCHITECTURE OVERVIEW:
 *
 * This class bridges Flink's async I/O with LangChain4j's LLM capabilities:
 *
 *   Flink Pipeline          This Class           LangChain4j           OpenAI
 *   ┌──────────┐           ┌─────────┐          ┌─────────┐          ┌──────┐
 *   │ Enriched │  ──────►  │ async   │  ──────► │ Chat    │  ──────► │ API  │
 *   │ Context  │           │ Invoke  │          │ Model   │          │      │
 *   └──────────┘           └────┬────┘          └─────────┘          └──────┘
 *                               │                                         │
 *                               │  CompletableFuture.thenAccept()        │
 *                               │ ◄───────────────────────────────────────┘
 *                               ▼
 *                          ResultFuture
 *                          (back to Flink)
 *
 * KEY CONCEPTS:
 *
 * 1. RichAsyncFunction:
 *    - Flink's interface for async I/O operations
 *    - asyncInvoke(): Called for each input element
 *    - timeout(): Called if operation exceeds configured timeout
 *    - ResultFuture: Callback mechanism to return results to Flink
 *
 * 2. Transient Service:
 *    - assistantService is marked 'transient' so it's NOT serialized
 *    - Initialized in open() method on each task
 *    - Each parallel task gets its own LangChain4j model instance
 *
 * 3. Error Handling:
 *    - Graceful fallback on LLM failures
 *    - Never throws exceptions (would mark record as failed)
 *    - Returns user-friendly error messages
 *
 * 4. Performance:
 *    - Non-blocking: Flink threads not blocked during LLM calls
 *    - Can handle 100+ concurrent requests per task
 *    - Throughput limited by OpenAI rate limits, not Flink
 */
public class ShoppingAssistantAsyncFunction extends RichAsyncFunction<EnrichedChatContext, AssistantResponse> {

    private static final Logger LOG = LoggerFactory.getLogger(ShoppingAssistantAsyncFunction.class);

    private final String apiKey;
    private final String modelName;

    // IMPORTANT: Mark as transient! This is not serializable and shouldn't be.
    // It will be initialized fresh on each task via the open() method.
    private transient ShoppingAssistantService assistantService;

    public ShoppingAssistantAsyncFunction(String apiKey, String modelName) {
        this.apiKey = apiKey;
        this.modelName = modelName;
    }

    /**
     * Initialize the LangChain4j service when this operator starts
     *
     * Called once per task (per parallel instance) when the job starts.
     * This is where we initialize non-serializable resources.
     */
    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        // Initialize LangChain4j service for this task
        this.assistantService = new ShoppingAssistantService(apiKey, modelName);
        LOG.info("Initialized Shopping Assistant with model: {}", modelName);
    }

    /**
     * The main async invocation method - called for each enriched chat message
     *
     * EXECUTION FLOW:
     * 1. Build prompt with context (conversation + basket + intent)
     * 2. Call LangChain4j service (returns CompletableFuture)
     * 3. Register success callback (thenAccept)
     * 4. Register error callback (exceptionally)
     * 5. Return immediately (non-blocking!)
     * 6. Flink manages the future and calls ResultFuture when done
     *
     * THREADING MODEL:
     * - This method runs on Flink's task thread
     * - LLM call happens on EXECUTOR_SERVICE thread pool
     * - Callbacks run on completion thread
     * - ResultFuture.complete() returns control to Flink
     *
     * @param context Enriched chat message with conversation and basket context
     * @param resultFuture Flink's callback to return the result
     */
    @Override
    public void asyncInvoke(EnrichedChatContext context, ResultFuture<AssistantResponse> resultFuture)
            throws Exception {

        // Step 1: Build prompt with shopping context
        // This includes system instructions, conversation history, basket info, and current query
        String prompt = buildShoppingPrompt(context);

        // Step 2: Call LLM async via LangChain4j
        // Returns immediately with CompletableFuture (non-blocking!)
        CompletableFuture<String> asyncResponse = assistantService.generateResponse(prompt);

        // Step 3: Register success callback - called when LLM responds
        asyncResponse.thenAccept(responseText -> {
            try {
                // Build the response object
                AssistantResponse response = new AssistantResponse();
                response.messageId = UUID.randomUUID().toString();
                response.sessionId = context.sessionId;
                response.responseText = responseText;  // LLM's generated text
                response.timestamp = System.currentTimeMillis();

                // Add product recommendations if this was a recommendation request
                if ("RECOMMENDATION".equals(context.userIntent) && context.availableRecommendations != null) {
                    response.recommendedProducts = context.availableRecommendations;
                }

                // Complete the ResultFuture - returns control to Flink
                resultFuture.complete(Collections.singleton(response));
            } catch (Exception e) {
                LOG.error("Failed to process LLM response", e);
                resultFuture.completeExceptionally(e);
            }
        }).exceptionally(throwable -> {
            // Step 4: Error handling - called if LLM call fails
            // IMPORTANT: Never throw exceptions! Always complete the future gracefully.
            LOG.error("LLM call failed for session: {}", context.sessionId, throwable);

            // Return a user-friendly fallback response instead of failing the record
            AssistantResponse fallbackResponse = new AssistantResponse();
            fallbackResponse.messageId = UUID.randomUUID().toString();
            fallbackResponse.sessionId = context.sessionId;
            fallbackResponse.responseText = "I'm sorry, I'm having trouble understanding. Could you please rephrase?";
            fallbackResponse.timestamp = System.currentTimeMillis();

            resultFuture.complete(Collections.singleton(fallbackResponse));
            return null;  // Required by exceptionally()
        });

        // Method returns here immediately (non-blocking!)
        // The CompletableFuture continues executing on background threads
        // Flink will be notified via resultFuture.complete() when done
    }

    @Override
    public void timeout(EnrichedChatContext input, ResultFuture<AssistantResponse> resultFuture)
            throws Exception {
        LOG.warn("Timeout for session: {}", input.sessionId);
        AssistantResponse timeoutResponse = new AssistantResponse();
        timeoutResponse.messageId = UUID.randomUUID().toString();
        timeoutResponse.sessionId = input.sessionId;
        timeoutResponse.responseText = "I'm taking a bit longer to respond. Please wait a moment.";
        timeoutResponse.timestamp = System.currentTimeMillis();
        resultFuture.complete(Collections.singleton(timeoutResponse));
    }

    /**
     * Build a structured prompt for the LLM
     *
     * PROMPT ENGINEERING BEST PRACTICES:
     * 1. Clear Role Definition - Tell the LLM what it is
     * 2. Specific Instructions - How it should behave
     * 3. Context First - Provide relevant background
     * 4. Structured Format - Use clear sections
     * 5. Explicit Query - End with the user's question
     *
     * PROMPT STRUCTURE:
     * ┌─────────────────────────────────────┐
     * │ System Role & Instructions          │  <- Who the LLM is
     * ├─────────────────────────────────────┤
     * │ Current Context (Basket)            │  <- Real-time state
     * ├─────────────────────────────────────┤
     * │ Conversation History                │  <- Past messages
     * ├─────────────────────────────────────┤
     * │ Additional Context (Recommendations)│  <- Extra info
     * ├─────────────────────────────────────┤
     * │ Current Query                       │  <- User's question
     * └─────────────────────────────────────┘
     *
     * @param context Enriched chat context with all relevant information
     * @return Formatted prompt string ready for LLM
     */
    private String buildShoppingPrompt(EnrichedChatContext context) {
        StringBuilder prompt = new StringBuilder();

        // Section 1: System Role & Behavioral Instructions
        prompt.append("You are a helpful shopping assistant for an e-commerce store. ");
        prompt.append("Be friendly, concise, and helpful. Focus on helping the customer find products and make purchases.\n\n");

        // Section 2: Current Basket Context (from Flink state)
        // This comes from the ChatContextEnricher which maintains BasketState
        // Helps LLM understand what the customer is shopping for
        if (context.basketContext != null && !context.basketContext.isEmpty()) {
            prompt.append("Current shopping cart:\n");
            prompt.append("- Items: ").append(context.basketContext.get("itemCount")).append("\n");
            prompt.append("- Total: $").append(String.format("%.2f", context.basketContext.get("totalValue"))).append("\n");
            prompt.append("- Products: ").append(context.basketContext.get("items")).append("\n\n");
        }

        // Section 3: Conversation History (from Flink state)
        // Last 10 messages from ConversationState
        // Essential for multi-turn conversations and context continuity
        if (context.conversationHistory != null && !context.conversationHistory.isEmpty()) {
            prompt.append("Recent conversation:\n");
            for (String msg : context.conversationHistory) {
                prompt.append(msg).append("\n");
            }
            prompt.append("\n");
        }

        // Section 4: Additional Context - Product Recommendations
        // Could come from recommendation engine or ML model
        // Helps LLM suggest specific products
        if (context.availableRecommendations != null && !context.availableRecommendations.isEmpty()) {
            prompt.append("Recommended products based on basket:\n");
            for (String rec : context.availableRecommendations) {
                prompt.append("- ").append(rec).append("\n");
            }
            prompt.append("\n");
        }

        // Section 5: Current User Query
        // The actual message from the customer
        // Format as dialogue to prime the LLM's response
        prompt.append("Customer: ").append(context.text).append("\n");
        prompt.append("Assistant: ");  // LLM will complete this

        return prompt.toString();
    }
}
