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
 * Async function that calls LLM to generate shopping assistant responses
 */
public class ShoppingAssistantAsyncFunction extends RichAsyncFunction<EnrichedChatContext, AssistantResponse> {

    private static final Logger LOG = LoggerFactory.getLogger(ShoppingAssistantAsyncFunction.class);

    private final String apiKey;
    private final String modelName;

    private transient ShoppingAssistantService assistantService;

    public ShoppingAssistantAsyncFunction(String apiKey, String modelName) {
        this.apiKey = apiKey;
        this.modelName = modelName;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        this.assistantService = new ShoppingAssistantService(apiKey, modelName);
    }

    @Override
    public void asyncInvoke(EnrichedChatContext context, ResultFuture<AssistantResponse> resultFuture)
            throws Exception {

        // Build prompt with shopping context
        String prompt = buildShoppingPrompt(context);

        // Call LLM async
        CompletableFuture<String> asyncResponse = assistantService.generateResponse(prompt);

        // Process the response
        asyncResponse.thenAccept(responseText -> {
            try {
                AssistantResponse response = new AssistantResponse();
                response.messageId = UUID.randomUUID().toString();
                response.sessionId = context.sessionId;
                response.responseText = responseText;
                response.timestamp = System.currentTimeMillis();

                // Extract product recommendations if applicable
                if ("RECOMMENDATION".equals(context.userIntent) && context.availableRecommendations != null) {
                    response.recommendedProducts = context.availableRecommendations;
                }

                resultFuture.complete(Collections.singleton(response));
            } catch (Exception e) {
                LOG.error("Failed to process LLM response", e);
                resultFuture.completeExceptionally(e);
            }
        }).exceptionally(throwable -> {
            LOG.error("LLM call failed", throwable);
            // Return a fallback response
            AssistantResponse fallbackResponse = new AssistantResponse();
            fallbackResponse.messageId = UUID.randomUUID().toString();
            fallbackResponse.sessionId = context.sessionId;
            fallbackResponse.responseText = "I'm sorry, I'm having trouble understanding. Could you please rephrase?";
            fallbackResponse.timestamp = System.currentTimeMillis();
            resultFuture.complete(Collections.singleton(fallbackResponse));
            return null;
        });
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

    private String buildShoppingPrompt(EnrichedChatContext context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are a helpful shopping assistant for an e-commerce store. ");
        prompt.append("Be friendly, concise, and helpful. Focus on helping the customer find products and make purchases.\n\n");

        // Add basket context
        if (context.basketContext != null && !context.basketContext.isEmpty()) {
            prompt.append("Current shopping cart:\n");
            prompt.append("- Items: ").append(context.basketContext.get("itemCount")).append("\n");
            prompt.append("- Total: $").append(String.format("%.2f", context.basketContext.get("totalValue"))).append("\n");
            prompt.append("- Products: ").append(context.basketContext.get("items")).append("\n\n");
        }

        // Add conversation history
        if (context.conversationHistory != null && !context.conversationHistory.isEmpty()) {
            prompt.append("Recent conversation:\n");
            for (String msg : context.conversationHistory) {
                prompt.append(msg).append("\n");
            }
            prompt.append("\n");
        }

        // Add recommendations if available
        if (context.availableRecommendations != null && !context.availableRecommendations.isEmpty()) {
            prompt.append("Recommended products based on basket:\n");
            for (String rec : context.availableRecommendations) {
                prompt.append("- ").append(rec).append("\n");
            }
            prompt.append("\n");
        }

        // Add user message
        prompt.append("Customer: ").append(context.text).append("\n");
        prompt.append("Assistant: ");

        return prompt.toString();
    }
}
