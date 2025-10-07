package com.ververica.composable_job.flink.assistant.operator;

import com.ververica.composable_job.flink.assistant.model.*;
import com.ververica.composable_job.model.ecommerce.EcommerceEvent;
import com.ververica.composable_job.model.ecommerce.EcommerceEventType;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.co.CoProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Enriches chat messages with conversation history and basket context
 */
public class ChatContextEnricher extends CoProcessFunction<ChatMessage, EcommerceEvent, EnrichedChatContext> {

    private static final Logger LOG = LoggerFactory.getLogger(ChatContextEnricher.class);

    private ValueState<ConversationState> conversationState;
    private ValueState<BasketState> basketState;

    @Override
    public void open(Configuration parameters) throws Exception {
        conversationState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("conversation", ConversationState.class));

        basketState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("basket", BasketState.class));
    }

    @Override
    public void processElement1(ChatMessage message, Context ctx, Collector<EnrichedChatContext> out)
            throws Exception {

        ConversationState conversation = conversationState.value();
        if (conversation == null) {
            conversation = new ConversationState(message.sessionId);
        }

        BasketState basket = basketState.value();
        if (basket == null) {
            basket = new BasketState(message.sessionId);
        }

        // Create enriched message
        EnrichedChatContext enriched = EnrichedChatContext.fromChatMessage(message);
        enriched.conversationHistory = conversation.getRecentMessages();
        enriched.basketContext = createBasketContext(basket);
        enriched.userIntent = detectIntent(message.text);

        // Update conversation history
        conversation.addMessage(message.text, "user");
        conversationState.update(conversation);

        out.collect(enriched);
        LOG.debug("Enriched chat message for session: {}", message.sessionId);
    }

    @Override
    public void processElement2(EcommerceEvent event, Context ctx, Collector<EnrichedChatContext> out)
            throws Exception {

        BasketState basket = basketState.value();
        if (basket == null) {
            basket = new BasketState(event.sessionId);
        }

        // Update basket based on event
        if (event.eventType == EcommerceEventType.ADD_TO_CART) {
            basket.addItem(event.productId, event.productName, event.value, event.categoryId);
        } else if (event.eventType == EcommerceEventType.REMOVE_FROM_CART) {
            basket.removeItem(event.productId);
        } else if (event.eventType == EcommerceEventType.CHECKOUT_COMPLETE ||
                   event.eventType == EcommerceEventType.ORDER_PLACED) {
            basket.clear();
        }

        basketState.update(basket);
        LOG.debug("Updated basket for session: {}", event.sessionId);
    }

    private Map<String, Object> createBasketContext(BasketState basket) {
        Map<String, Object> context = new HashMap<>();
        context.put("itemCount", basket.getTotalItemCount());
        context.put("totalValue", basket.getTotalValue());
        context.put("categories", basket.getCategories());
        context.put("items", basket.getProductNames());
        return context;
    }

    private String detectIntent(String text) {
        String lowerText = text.toLowerCase();

        if (lowerText.contains("recommend") || lowerText.contains("suggest")) {
            return "RECOMMENDATION";
        } else if (lowerText.contains("cart") || lowerText.contains("basket")) {
            return "CART_INQUIRY";
        } else if (lowerText.contains("price") || lowerText.contains("cost")) {
            return "PRICE_INQUIRY";
        } else if (lowerText.contains("find") || lowerText.contains("search")) {
            return "PRODUCT_SEARCH";
        } else if (lowerText.contains("help")) {
            return "HELP";
        }

        return "GENERAL";
    }
}
