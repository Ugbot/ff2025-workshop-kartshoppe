package com.ververica.composable_job.flink.recommendations.shared.processor;

import com.ververica.composable_job.model.ecommerce.EcommerceEvent;
import com.ververica.composable_job.model.ecommerce.EcommerceEventType;
import com.ververica.composable_job.flink.recommendations.ml.BasketPattern;
import com.ververica.composable_job.flink.recommendations.ml.ProductRecommender;
import com.ververica.composable_job.flink.recommendations.shared.model.CurrentBasket;
import com.ververica.composable_job.flink.recommendations.shared.model.RecommendationEvent;
import org.apache.flink.api.common.state.BroadcastState;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.state.ReadOnlyBroadcastState;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.co.KeyedBroadcastProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates product recommendations using broadcast state patterns.
 *
 * PATTERN: Broadcast State (Pattern 02 from Recommendations)
 *
 * ARCHITECTURE:
 * <pre>
 * Stream 1 (High Volume): Shopping Events
 *   ├─→ ADD_TO_CART
 *   ├─→ VIEW_PRODUCT
 *   └─→ PURCHASE
 *
 * Stream 2 (Low Volume): Learned Patterns (BROADCAST)
 *   └─→ BasketPattern (broadcast to ALL tasks)
 *
 * Connected Streams:
 *   processElement1() → Handle shopping events (keyed state)
 *   processElement2() → Update broadcast patterns (broadcast state)
 * </pre>
 *
 * TWO TYPES OF STATE:
 *
 * 1. KEYED STATE (per sessionId):
 *    - basketState: Current basket for this session
 *    - Each parallel instance only sees its own keys
 *
 * 2. BROADCAST STATE (shared across all tasks):
 *    - PATTERNS_DESCRIPTOR: All learned basket patterns
 *    - Every parallel instance has identical copy
 *    - Read-only in processElement1()
 *    - Read-write in processElement2()
 *
 * RECOMMENDATION STRATEGIES:
 *
 * 1. BASKET_BASED:
 *    - Triggered by: ADD_TO_CART
 *    - Logic: Find patterns where antecedents match current basket
 *    - Example: Basket has [laptop, keyboard] → recommend "mouse" (if pattern exists)
 *
 * 2. VIEW_BASED:
 *    - Triggered by: VIEW_PRODUCT
 *    - Logic: Find patterns where viewed product is antecedent
 *    - Example: Viewing "laptop" → recommend "mouse", "keyboard"
 *
 * LEARNING OBJECTIVES:
 * - Understanding broadcast state vs keyed state
 * - Read-only vs read-write broadcast contexts
 * - Model inference with broadcast patterns
 * - Combining multiple state types in one function
 *
 * @see com.ververica.composable_job.flink.recommendations.patterns.02_broadcast_state
 */
public class RecommendationGeneratorFunction
        extends KeyedBroadcastProcessFunction<String, EcommerceEvent, BasketPattern, RecommendationEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(RecommendationGeneratorFunction.class);

    // Configuration
    private static final double MIN_CONFIDENCE = 0.5;  // Only recommend if confidence > 50%
    private static final int MAX_RECOMMENDATIONS = 5;  // Limit recommendations per event
    private static final int MAX_PATTERNS_PER_TASK = 5000; // Prevent broadcast state from growing too large

    // PATTERN: Keyed State (per session)
    private ValueState<CurrentBasket> basketState;
    private ListState<BasketPattern> patternsState; // For fallback/local patterns

    // Transient (not checkpointed)
    private transient ProductRecommender recommender;

    @Override
    public void open(Configuration parameters) throws Exception {
        // Initialize keyed state
        basketState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("current-basket", CurrentBasket.class));

        patternsState = getRuntimeContext().getListState(
            new ListStateDescriptor<>("learned-patterns", BasketPattern.class));

        // Initialize recommender (transient, not checkpointed)
        recommender = new ProductRecommender();

        LOG.info("RecommendationGeneratorFunction initialized");
    }

    /**
     * PATTERN: Broadcast State - Process shopping events
     *
     * This method:
     * 1. Updates KEYED STATE (basketState) for this session
     * 2. Reads from READ-ONLY BROADCAST STATE (patterns)
     * 3. Generates recommendations based on current basket + patterns
     *
     * @param event Shopping event (ADD_TO_CART, VIEW_PRODUCT, etc.)
     * @param ctx Context with read-only broadcast state
     * @param out Collector for recommendation events
     */
    @Override
    public void processElement(
            EcommerceEvent event,
            ReadOnlyContext ctx,
            Collector<RecommendationEvent> out) throws Exception {

        // STEP 1: Get or create basket for this session (KEYED STATE)
        CurrentBasket basket = basketState.value();
        if (basket == null) {
            basket = new CurrentBasket(event.sessionId, event.userId);
        }

        // STEP 2: Process event based on type
        switch (event.eventType) {
            case ADD_TO_CART:
                handleAddToCart(event, basket, ctx, out);
                break;

            case PRODUCT_VIEW:
                handleViewProduct(event, basket, ctx, out);
                break;

            case ORDER_PLACED:
            case CHECKOUT_COMPLETE:
            case PAYMENT_PROCESSED:
                // Clear basket after purchase
                basketState.clear();
                return; // Exit early

            default:
                LOG.trace("Unhandled event type: {}", event.eventType);
        }

        // STEP 3: Update basket state
        basketState.update(basket);
    }

    /**
     * PATTERN: Broadcast State - Update pattern model
     *
     * This method:
     * 1. Receives new patterns from PatternMinerFunction
     * 2. Updates READ-WRITE BROADCAST STATE
     * 3. Makes patterns available to ALL parallel instances
     *
     * @param pattern Newly mined basket pattern
     * @param ctx Context with read-write broadcast state
     * @param out Collector (not used, patterns don't emit here)
     */
    @Override
    public void processBroadcastElement(
            BasketPattern pattern,
            Context ctx,
            Collector<RecommendationEvent> out) throws Exception {

        // STEP 1: Access broadcast state (READ-WRITE)
        BroadcastState<String, BasketPattern> broadcastState =
            ctx.getBroadcastState(PatternBroadcastState.PATTERNS_DESCRIPTOR);

        // STEP 2: Generate pattern ID
        String patternId = PatternBroadcastState.generatePatternId(pattern);

        // STEP 3: Update if new or better pattern
        BasketPattern existingPattern = broadcastState.get(patternId);
        if (PatternBroadcastState.shouldUpdatePattern(existingPattern, pattern)) {
            broadcastState.put(patternId, pattern);
            LOG.debug("Updated broadcast pattern: {}", patternId);
        }

        // STEP 4: Also store in keyed state for this task (fallback)
        patternsState.add(pattern);

        // STEP 5: Clean up old patterns (prevent unbounded growth)
        cleanupBroadcastState(broadcastState);

        // Update recommender model (transient)
        recommender.addTrainingPattern(pattern);
    }

    // ========================================
    // Event Handlers
    // ========================================

    /**
     * Handle ADD_TO_CART: Generate basket-based recommendations
     *
     * STRATEGY:
     * 1. Add product to current basket
     * 2. Find patterns where antecedents match basket items
     * 3. Recommend consequents with high confidence
     */
    private void handleAddToCart(
            EcommerceEvent event,
            CurrentBasket basket,
            ReadOnlyContext ctx,
            Collector<RecommendationEvent> out) throws Exception {

        // Update basket
        basket.products.add(event.productId);

        // Generate recommendations based on current basket
        List<String> recommendations = generateBasketRecommendations(
            basket.products, ctx);

        if (!recommendations.isEmpty()) {
            RecommendationEvent rec = new RecommendationEvent();
            rec.sessionId = event.sessionId;
            rec.userId = event.userId;
            rec.recommendationType = "BASKET_BASED";
            rec.recommendedProducts = recommendations;
            rec.confidence = 0.75; // Average confidence
            rec.timestamp = System.currentTimeMillis();
            rec.triggerEvent = "ADD_TO_CART";
            rec.context = new HashMap<>();
            rec.context.put("basketSize", String.valueOf(basket.products.size()));

            out.collect(rec);

            LOG.debug("Generated basket-based recommendations for {}: {}",
                event.sessionId, recommendations);
        }
    }

    /**
     * Handle VIEW_PRODUCT: Generate view-based recommendations
     *
     * STRATEGY:
     * 1. Track viewed product
     * 2. Find patterns where viewed product is antecedent
     * 3. Recommend consequents
     */
    private void handleViewProduct(
            EcommerceEvent event,
            CurrentBasket basket,
            ReadOnlyContext ctx,
            Collector<RecommendationEvent> out) throws Exception {

        // Update view history
        basket.viewHistory.add(event.productId);

        // Generate view-based recommendations
        List<String> recommendations = generateViewRecommendations(
            event.productId, ctx);

        if (!recommendations.isEmpty()) {
            RecommendationEvent rec = new RecommendationEvent();
            rec.sessionId = event.sessionId;
            rec.userId = event.userId;
            rec.recommendationType = "VIEW_BASED";
            rec.recommendedProducts = recommendations;
            rec.confidence = 0.65; // Slightly lower confidence for views
            rec.timestamp = System.currentTimeMillis();
            rec.triggerEvent = "VIEW_PRODUCT";
            rec.context = new HashMap<>();
            rec.context.put("viewedProduct", event.productId);

            out.collect(rec);

            LOG.debug("Generated view-based recommendations for {}: {}",
                event.sessionId, recommendations);
        }
    }

    // ========================================
    // Recommendation Logic
    // ========================================

    /**
     * Generate recommendations based on current basket
     *
     * ALGORITHM:
     * 1. Access READ-ONLY broadcast state
     * 2. Find patterns where antecedents ⊆ current basket
     * 3. Recommend consequents not already in basket
     * 4. Filter by confidence threshold
     * 5. Limit to top N recommendations
     */
    private List<String> generateBasketRecommendations(
            List<String> currentProducts,
            ReadOnlyContext ctx) throws Exception {

        Set<String> recommendations = new HashSet<>();
        Set<String> currentSet = new HashSet<>(currentProducts);

        // PATTERN: Read-only broadcast state access
        ReadOnlyBroadcastState<String, BasketPattern> patterns =
            ctx.getBroadcastState(PatternBroadcastState.PATTERNS_DESCRIPTOR);

        // Scan all patterns
        for (Map.Entry<String, BasketPattern> entry : patterns.immutableEntries()) {
            BasketPattern pattern = entry.getValue();

            // Check if current basket contains all antecedents
            if (currentSet.containsAll(pattern.getAntecedents()) &&
                !currentSet.contains(pattern.getConsequent())) {

                // Only recommend if confidence is high enough
                if (pattern.getConfidence() > MIN_CONFIDENCE) {
                    recommendations.add(pattern.getConsequent());
                }
            }
        }

        // Limit to top N
        return recommendations.stream()
            .limit(MAX_RECOMMENDATIONS)
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Generate recommendations based on viewed product
     *
     * ALGORITHM:
     * 1. Find patterns where viewed product is in antecedents
     * 2. Recommend consequents
     * 3. Sort by confidence
     */
    private List<String> generateViewRecommendations(
            String productId,
            ReadOnlyContext ctx) throws Exception {

        Set<String> recommendations = new HashSet<>();

        // Access broadcast state
        ReadOnlyBroadcastState<String, BasketPattern> patterns =
            ctx.getBroadcastState(PatternBroadcastState.PATTERNS_DESCRIPTOR);

        // Find patterns where this product is an antecedent
        for (Map.Entry<String, BasketPattern> entry : patterns.immutableEntries()) {
            BasketPattern pattern = entry.getValue();

            if (pattern.getAntecedents().contains(productId)) {
                recommendations.add(pattern.getConsequent());
            }
        }

        return recommendations.stream()
            .limit(3) // Fewer recommendations for views
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Clean up old patterns from broadcast state
     * PATTERN: State Size Management
     */
    private void cleanupBroadcastState(BroadcastState<String, BasketPattern> broadcastState)
            throws Exception {

        // Count patterns
        int count = 0;
        for (Map.Entry<String, BasketPattern> entry : broadcastState.entries()) {
            count++;
        }

        if (count > MAX_PATTERNS_PER_TASK) {
            // Remove oldest patterns (simple strategy: keep most recent by timestamp)
            List<Map.Entry<String, BasketPattern>> allPatterns = new ArrayList<>();
            broadcastState.entries().forEach(allPatterns::add);

            // Sort by timestamp (newest first)
            allPatterns.sort((a, b) ->
                Long.compare(b.getValue().getTimestamp(), a.getValue().getTimestamp()));

            // Clear state
            broadcastState.clear();

            // Keep only newest N patterns
            for (int i = 0; i < MAX_PATTERNS_PER_TASK && i < allPatterns.size(); i++) {
                Map.Entry<String, BasketPattern> entry = allPatterns.get(i);
                broadcastState.put(entry.getKey(), entry.getValue());
            }

            LOG.info("Cleaned up broadcast state, kept {} patterns", MAX_PATTERNS_PER_TASK);
        }
    }
}
