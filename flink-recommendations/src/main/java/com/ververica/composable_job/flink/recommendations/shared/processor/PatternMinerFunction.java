package com.ververica.composable_job.flink.recommendations.shared.processor;

import com.ververica.composable_job.flink.recommendations.ml.BasketPattern;
import com.ververica.composable_job.flink.recommendations.shared.model.BasketCompletion;
import com.ververica.composable_job.flink.recommendations.shared.model.BasketItem;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mines association rules from completed shopping baskets.
 *
 * PATTERN: Keyed State (Advanced Usage)
 * Demonstrates sophisticated state management for pattern mining.
 *
 * ALGORITHM: Association Rule Mining (Market Basket Analysis)
 * Discovers patterns like: "Customers who bought X also bought Y"
 *
 * METRICS CALCULATED:
 * 1. Confidence: P(Y|X) = support(X,Y) / support(X)
 *    - Probability that Y is purchased given X was purchased
 *    - Higher is better (threshold: 0.3 for full baskets, 0.4 for pairs)
 *
 * 2. Support: P(X,Y) = count(X,Y) / total_baskets
 *    - How often X and Y appear together
 *    - Minimum threshold: 0.01 (1%)
 *
 * 3. Lift: confidence(X→Y) / support(Y)
 *    - How much more likely Y is purchased when X is purchased
 *    - Lift > 1: positive correlation
 *    - Lift = 1: no correlation
 *    - Lift < 1: negative correlation
 *
 * STATE MANAGED:
 * <pre>
 * historicalBasketsState: ListState<BasketCompletion>
 *   → Stores last 1000 completed baskets for analysis
 *
 * itemFrequencyState: MapState<productId, count>
 *   → Tracks how often each item appears
 *
 * itemPairsState: MapState<productId, Map<productId, count>>
 *   → Tracks co-occurrence of item pairs (not currently used)
 *
 * basketCountState: ValueState<Integer>
 *   → Total number of baskets processed
 * </pre>
 *
 * OUTPUT:
 * - BasketPattern: Association rules for recommendations
 *   Example: {antecedents: ["laptop"], consequent: "mouse", confidence: 0.75}
 *
 * LEARNING OBJECTIVES:
 * - Managing multiple state types concurrently
 * - State size management (keep last N items)
 * - Calculating statistical metrics from state
 * - Streaming pattern mining algorithms
 *
 * @see com.ververica.composable_job.flink.inventory.patterns.02_keyed_state
 */
public class PatternMinerFunction
        extends KeyedProcessFunction<String, BasketCompletion, BasketPattern> {

    private static final Logger LOG = LoggerFactory.getLogger(PatternMinerFunction.class);

    // Configuration thresholds
    private static final double MIN_CONFIDENCE_FULL = 0.3;  // For full basket patterns
    private static final double MIN_CONFIDENCE_PAIR = 0.4;  // For pair patterns
    private static final double MIN_SUPPORT = 0.01;         // 1% minimum support
    private static final int MAX_HISTORICAL_BASKETS = 1000; // Keep last N baskets

    // PATTERN: Keyed State - Multiple state types
    private ListState<BasketCompletion> historicalBasketsState;
    private MapState<String, Integer> itemFrequencyState;
    private MapState<String, Map<String, Integer>> itemPairsState;
    private ValueState<Integer> basketCountState;

    @Override
    public void open(Configuration parameters) throws Exception {
        // Initialize state descriptors
        historicalBasketsState = getRuntimeContext().getListState(
            new ListStateDescriptor<>("historical-baskets", BasketCompletion.class));

        itemFrequencyState = getRuntimeContext().getMapState(
            new MapStateDescriptor<>("item-frequency", String.class, Integer.class));

        // Note: itemPairsState not currently used, commenting out to avoid type issues
        // itemPairsState = getRuntimeContext().getMapState(
        //     new MapStateDescriptor<>("item-pairs", String.class,
        //         TypeInformation.of(new TypeHint<Map<String, Integer>>() {})));

        basketCountState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("basket-count", Integer.class, 0));

        LOG.info("PatternMinerFunction initialized");
    }

    @Override
    public void processElement(
            BasketCompletion basket,
            Context ctx,
            Collector<BasketPattern> out) throws Exception {

        // STEP 1: Store basket for future analysis
        historicalBasketsState.add(basket);

        // STEP 2: Update basket count
        Integer count = basketCountState.value();
        int newCount = count + 1;
        basketCountState.update(newCount);

        // STEP 3: Update item frequency statistics
        updateItemFrequencies(basket);

        // STEP 4: Mine patterns from current basket
        List<String> productIds = basket.items.stream()
            .map(item -> item.productId)
            .collect(Collectors.toList());

        // Generate association rules
        mineAssociationRules(basket, productIds, newCount, out);

        // STEP 5: Clean up old baskets (keep last 1000)
        cleanupOldBaskets(newCount);
    }

    /**
     * Update item frequency counts
     * PATTERN: MapState for counting
     */
    private void updateItemFrequencies(BasketCompletion basket) throws Exception {
        for (BasketItem item : basket.items) {
            Integer freq = itemFrequencyState.get(item.productId);
            itemFrequencyState.put(item.productId, freq == null ? 1 : freq + 1);
        }
    }

    /**
     * Mine association rules from the basket
     *
     * Strategy:
     * 1. For each item as consequent
     * 2. Use other items as antecedents
     * 3. Calculate confidence, support, lift
     * 4. Emit if thresholds met
     */
    private void mineAssociationRules(
            BasketCompletion basket,
            List<String> productIds,
            int totalBaskets,
            Collector<BasketPattern> out) throws Exception {

        // Generate rules for each item as consequent
        for (int i = 0; i < productIds.size(); i++) {
            String consequent = productIds.get(i);
            List<String> antecedents = new ArrayList<>();

            // Create antecedents from other items
            for (int j = 0; j < productIds.size(); j++) {
                if (i != j) {
                    antecedents.add(productIds.get(j));
                }
            }

            if (!antecedents.isEmpty()) {
                // FULL BASKET PATTERN: All other items → this item
                double confidence = calculateConfidence(antecedents, consequent);
                double support = calculateSupport(antecedents, consequent, totalBaskets);

                if (confidence > MIN_CONFIDENCE_FULL && support > MIN_SUPPORT) {
                    emitPattern(basket, antecedents, consequent, confidence, support, totalBaskets, out);
                }

                // PAIR PATTERNS: Single item → this item (higher confidence threshold)
                if (antecedents.size() > 1) {
                    for (String antecedent : antecedents) {
                        double pairConfidence = calculateConfidence(
                            Collections.singletonList(antecedent), consequent);

                        if (pairConfidence > MIN_CONFIDENCE_PAIR) {
                            double pairSupport = calculateSupport(
                                Collections.singletonList(antecedent), consequent, totalBaskets);

                            emitPattern(basket, Collections.singletonList(antecedent),
                                consequent, pairConfidence, pairSupport, totalBaskets, out);
                        }
                    }
                }
            }
        }
    }

    /**
     * Calculate confidence: P(consequent | antecedents)
     *
     * Confidence = count(antecedents AND consequent) / count(antecedents)
     *
     * ALGORITHM:
     * 1. Count baskets containing all antecedents
     * 2. Count baskets containing antecedents AND consequent
     * 3. confidence = (2) / (1)
     */
    private double calculateConfidence(List<String> antecedents, String consequent) throws Exception {
        int coOccurrence = 0;
        int antecedentOccurrence = 0;

        // Scan historical baskets
        for (BasketCompletion basket : historicalBasketsState.get()) {
            Set<String> basketProducts = basket.items.stream()
                .map(item -> item.productId)
                .collect(Collectors.toSet());

            if (basketProducts.containsAll(antecedents)) {
                antecedentOccurrence++;
                if (basketProducts.contains(consequent)) {
                    coOccurrence++;
                }
            }
        }

        if (antecedentOccurrence == 0) {
            // First occurrence - return default confidence
            return 0.5;
        }

        return (double) coOccurrence / antecedentOccurrence;
    }

    /**
     * Calculate support: P(antecedents AND consequent)
     *
     * Support = count(antecedents AND consequent) / total_baskets
     */
    private double calculateSupport(
            List<String> antecedents,
            String consequent,
            int totalBaskets) throws Exception {

        if (totalBaskets == 0) return 0;

        int occurrence = 0;
        for (BasketCompletion basket : historicalBasketsState.get()) {
            Set<String> basketProducts = basket.items.stream()
                .map(item -> item.productId)
                .collect(Collectors.toSet());

            if (basketProducts.containsAll(antecedents) && basketProducts.contains(consequent)) {
                occurrence++;
            }
        }

        return (double) occurrence / totalBaskets;
    }

    /**
     * Calculate lift: confidence(X→Y) / support(Y)
     *
     * Lift = confidence / P(consequent)
     *
     * Interpretation:
     * - Lift > 1: X and Y positively correlated
     * - Lift = 1: X and Y independent
     * - Lift < 1: X and Y negatively correlated
     */
    private double calculateLift(double confidence, String consequent, int totalBaskets) throws Exception {
        Integer consequentFreq = itemFrequencyState.get(consequent);
        if (consequentFreq == null || totalBaskets == 0) {
            return 1.0;
        }

        double consequentProb = (double) consequentFreq / totalBaskets;
        if (consequentProb == 0) {
            return 1.0;
        }

        return confidence / consequentProb;
    }

    /**
     * Create and emit a BasketPattern
     */
    private void emitPattern(
            BasketCompletion basket,
            List<String> antecedents,
            String consequent,
            double confidence,
            double support,
            int totalBaskets,
            Collector<BasketPattern> out) throws Exception {

        BasketPattern pattern = new BasketPattern();
        pattern.setAntecedents(antecedents);
        pattern.setConsequent(consequent);
        pattern.setConfidence(confidence);
        pattern.setSupport(support);
        pattern.setLift(calculateLift(confidence, consequent, totalBaskets));
        pattern.setUserId(basket.userId);
        pattern.setSessionId(basket.sessionId);
        pattern.setTimestamp(System.currentTimeMillis());

        // Get category from basket items
        basket.items.stream()
            .filter(item -> item.productId.equals(consequent))
            .findFirst()
            .ifPresent(item -> pattern.setCategory(item.category));

        out.collect(pattern);

        LOG.debug("Mined pattern: {} → {} (conf={:.2f}, sup={:.2f}, lift={:.2f})",
            antecedents, consequent, confidence, support, pattern.getLift());
    }

    /**
     * Clean up old baskets to prevent state from growing unbounded
     * PATTERN: State Size Management
     */
    private void cleanupOldBaskets(int count) throws Exception {
        if (count > MAX_HISTORICAL_BASKETS) {
            List<BasketCompletion> allBaskets = new ArrayList<>();
            historicalBasketsState.get().forEach(allBaskets::add);

            if (allBaskets.size() > MAX_HISTORICAL_BASKETS) {
                // Keep only last N baskets
                historicalBasketsState.clear();
                allBaskets.stream()
                    .skip(allBaskets.size() - MAX_HISTORICAL_BASKETS)
                    .forEach(b -> {
                        try {
                            historicalBasketsState.add(b);
                        } catch (Exception e) {
                            LOG.error("Failed to update historical baskets", e);
                        }
                    });

                LOG.debug("Cleaned up old baskets, kept last {}", MAX_HISTORICAL_BASKETS);
            }
        }
    }
}
