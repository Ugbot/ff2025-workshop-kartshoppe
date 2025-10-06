package com.ververica.composable_job.flink.recommendations.shared.processor;

import com.ververica.composable_job.model.ecommerce.EcommerceEvent;
import com.ververica.composable_job.model.ecommerce.EcommerceEventType;
import com.ververica.composable_job.flink.recommendations.shared.model.ActiveBasket;
import com.ververica.composable_job.flink.recommendations.shared.model.BasketCompletion;
import com.ververica.composable_job.flink.recommendations.shared.model.BasketItem;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks shopping baskets and emits completed baskets for pattern mining.
 *
 * PATTERNS DEMONSTRATED:
 *
 * 1. KEYED STATE (Pattern 02 from Inventory)
 *    - Maintains basket metadata per session
 *    - Stores list of basket items
 *    - Tracks last activity timestamp
 *
 * 2. TIMERS (Pattern 03 from Inventory)
 *    - Registers processing-time timer for session timeout
 *    - Cleans up expired sessions after 30 minutes of inactivity
 *
 * DATA FLOW:
 * <pre>
 * EcommerceEvent (keyed by sessionId)
 *   │
 *   ├─→ ADD_TO_CART → Store item in ListState
 *   ├─→ REMOVE_FROM_CART → Remove item from ListState
 *   ├─→ PURCHASE/CHECKOUT → Emit BasketCompletion, clear state
 *   └─→ VIEW_PRODUCT → Track in basket metadata
 *
 * State managed:
 * - basketState: ValueState<ActiveBasket> (metadata)
 * - itemsState: ListState<BasketItem> (items in basket)
 * - lastActivityState: ValueState<Long> (for timeout detection)
 * </pre>
 *
 * OUTPUT:
 * - BasketCompletion events (when purchase detected)
 *
 * LEARNING OBJECTIVES:
 * - Managing multiple state types (Value, List)
 * - Using timers for session timeout
 * - State cleanup on purchase or timeout
 * - Keyed stream processing
 *
 * @see com.ververica.composable_job.flink.inventory.patterns.02_keyed_state
 * @see com.ververica.composable_job.flink.inventory.patterns.03_timers
 */
public class BasketTrackerFunction
        extends KeyedProcessFunction<String, EcommerceEvent, BasketCompletion> {

    private static final Logger LOG = LoggerFactory.getLogger(BasketTrackerFunction.class);
    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes

    // PATTERN: Keyed State - Basket metadata
    private ValueState<ActiveBasket> basketState;

    // PATTERN: Keyed State - List of items
    private ListState<BasketItem> itemsState;

    // PATTERN: Timers - Track last activity for timeout
    private ValueState<Long> lastActivityState;

    @Override
    public void open(Configuration parameters) throws Exception {
        // Initialize keyed state descriptors
        basketState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("active-basket", ActiveBasket.class));

        itemsState = getRuntimeContext().getListState(
            new ListStateDescriptor<>("basket-items", BasketItem.class));

        lastActivityState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("last-activity", Long.class));

        LOG.info("BasketTrackerFunction initialized");
    }

    @Override
    public void processElement(
            EcommerceEvent event,
            Context ctx,
            Collector<BasketCompletion> out) throws Exception {

        // STEP 1: Get or create basket for this session
        ActiveBasket basket = basketState.value();
        if (basket == null) {
            basket = new ActiveBasket(event.sessionId, event.userId);
            LOG.debug("Created new basket for session: {}", event.sessionId);
        }

        // STEP 2: Update last activity timestamp (for timeout detection)
        long now = System.currentTimeMillis();
        lastActivityState.update(now);

        // STEP 3: Process event based on type
        switch (event.eventType) {
            case ADD_TO_CART:
                handleAddToCart(event, basket);
                break;

            case REMOVE_FROM_CART:
                handleRemoveFromCart(event, basket);
                break;

            case ORDER_PLACED:
            case CHECKOUT_COMPLETE:
            case PAYMENT_PROCESSED:
                // Basket completed - emit for pattern mining
                handlePurchase(event, basket, out);
                // Clear state after purchase
                clearState();
                return; // Exit early, state is cleared

            case PRODUCT_VIEW:
                handleViewProduct(event, basket);
                break;

            default:
                LOG.trace("Unhandled event type: {}", event.eventType);
        }

        // STEP 4: Update basket state
        basketState.update(basket);

        // STEP 5: Register timer for session timeout (30 minutes from now)
        // PATTERN: Timers
        ctx.timerService().registerProcessingTimeTimer(now + SESSION_TIMEOUT_MS);
    }

    /**
     * PATTERN: Timers - Called when timer fires
     * Handles session timeout cleanup
     */
    @Override
    public void onTimer(
            long timestamp,
            OnTimerContext ctx,
            Collector<BasketCompletion> out) throws Exception {

        Long lastActivity = lastActivityState.value();
        if (lastActivity != null && timestamp - lastActivity >= SESSION_TIMEOUT_MS) {
            // Session expired - clean up state
            LOG.debug("Session timeout for {}", ctx.getCurrentKey());
            clearState();
        }
    }

    // ========================================
    // Event Handlers
    // ========================================

    /**
     * Handle ADD_TO_CART event
     * PATTERN: Keyed State (ListState)
     */
    private void handleAddToCart(EcommerceEvent event, ActiveBasket basket) throws Exception {
        BasketItem item = new BasketItem(
            event.productId,
            event.productName,
            event.categoryId,
            event.value,
            event.quantity > 0 ? event.quantity : 1,
            System.currentTimeMillis()
        );

        // Add to list state
        itemsState.add(item);

        // Update basket metadata
        basket.itemCount++;
        basket.totalValue += event.value;

        LOG.debug("Added {} to basket {} (total items: {})",
            event.productId, event.sessionId, basket.itemCount);
    }

    /**
     * Handle REMOVE_FROM_CART event
     * PATTERN: Keyed State (ListState manipulation)
     */
    private void handleRemoveFromCart(EcommerceEvent event, ActiveBasket basket) throws Exception {
        // Read all items from list state
        List<BasketItem> items = new ArrayList<>();
        itemsState.get().forEach(items::add);

        // Remove matching item
        items.removeIf(item -> item.productId.equals(event.productId));

        // Update list state
        itemsState.clear();
        items.forEach(item -> {
            try {
                itemsState.add(item);
            } catch (Exception e) {
                LOG.error("Failed to update items state", e);
            }
        });

        // Update basket metadata
        basket.itemCount = items.size();

        LOG.debug("Removed {} from basket {} (remaining items: {})",
            event.productId, event.sessionId, basket.itemCount);
    }

    /**
     * Handle PURCHASE/CHECKOUT event
     * Emits BasketCompletion for pattern mining
     */
    private void handlePurchase(
            EcommerceEvent event,
            ActiveBasket basket,
            Collector<BasketCompletion> out) throws Exception {

        // Get all purchased items
        List<BasketItem> purchasedItems = new ArrayList<>();
        itemsState.get().forEach(purchasedItems::add);

        if (!purchasedItems.isEmpty()) {
            // Create basket completion event
            BasketCompletion completion = new BasketCompletion();
            completion.sessionId = event.sessionId;
            completion.userId = event.userId;
            completion.items = purchasedItems;
            completion.totalValue = basket.totalValue;
            completion.completionTime = System.currentTimeMillis();
            completion.itemCount = purchasedItems.size();

            // Emit for pattern mining
            out.collect(completion);

            LOG.info("Basket completed: session={}, items={}, value={}",
                event.sessionId, purchasedItems.size(), basket.totalValue);
        } else {
            LOG.warn("Purchase event for empty basket: {}", event.sessionId);
        }
    }

    /**
     * Handle VIEW_PRODUCT event
     * Tracks viewed products for context (limited to 50)
     */
    private void handleViewProduct(EcommerceEvent event, ActiveBasket basket) {
        basket.viewedProducts.add(event.productId);

        // Keep only last 50 viewed products
        if (basket.viewedProducts.size() > 50) {
            basket.viewedProducts.remove(0);
        }
    }

    /**
     * Clear all state for this session
     * PATTERN: State Cleanup
     */
    private void clearState() throws Exception {
        basketState.clear();
        itemsState.clear();
        lastActivityState.clear();
    }
}
