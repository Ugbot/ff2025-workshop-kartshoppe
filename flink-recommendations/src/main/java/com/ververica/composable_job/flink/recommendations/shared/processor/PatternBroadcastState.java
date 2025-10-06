package com.ververica.composable_job.flink.recommendations.shared.processor;

import com.ververica.composable_job.flink.recommendations.ml.BasketPattern;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;

/**
 * Broadcast state descriptor for pattern distribution.
 *
 * PATTERN: Broadcast State (Pattern 02 from Recommendations)
 *
 * PURPOSE:
 * Distributes learned basket patterns to ALL parallel instances of
 * RecommendationGeneratorFunction so each can generate recommendations
 * using the same shared model.
 *
 * HOW IT WORKS:
 * <pre>
 * Pattern Miner
 *   │
 *   ├─→ Pattern A ──┐
 *   ├─→ Pattern B ──┼─→ Broadcast Stream
 *   └─→ Pattern C ──┘       │
 *                            ├─→ Task 1 (receives all patterns)
 *                            ├─→ Task 2 (receives all patterns)
 *                            ├─→ Task 3 (receives all patterns)
 *                            └─→ Task 4 (receives all patterns)
 *
 * Each task maintains identical copy of patterns in broadcast state.
 * When new pattern arrives, all tasks update their broadcast state.
 * </pre>
 *
 * STATE STRUCTURE:
 * - Key: patternId (String) = "{antecedents}_{consequent}"
 * - Value: BasketPattern containing confidence, support, lift
 *
 * USAGE EXAMPLE:
 * <pre>{@code
 * // Create broadcast stream
 * BroadcastStream<BasketPattern> patterns = minedPatterns
 *     .broadcast(PatternBroadcastState.PATTERNS_DESCRIPTOR);
 *
 * // Connect with events stream
 * DataStream<Recommendation> recs = events
 *     .keyBy(e -> e.sessionId)
 *     .connect(patterns)
 *     .process(new RecommendationGeneratorFunction());
 * }</pre>
 *
 * LEARNING OBJECTIVES:
 * - Understanding broadcast state concept
 * - Model distribution pattern
 * - Shared read-only state across tasks
 *
 * @see com.ververica.composable_job.flink.recommendations.patterns.02_broadcast_state
 */
public class PatternBroadcastState {

    /**
     * Broadcast state descriptor for basket patterns.
     *
     * This descriptor defines:
     * - Name: "basket-patterns-broadcast"
     * - Key type: String (pattern identifier)
     * - Value type: BasketPattern (the learned pattern)
     *
     * The descriptor is used to:
     * 1. Create the broadcast stream
     * 2. Access broadcast state in RecommendationGeneratorFunction
     */
    public static final MapStateDescriptor<String, BasketPattern> PATTERNS_DESCRIPTOR =
        new MapStateDescriptor<>(
            "basket-patterns-broadcast",
            TypeInformation.of(String.class),
            TypeInformation.of(new TypeHint<BasketPattern>() {})
        );

    /**
     * Generate a unique pattern ID
     *
     * Format: "antecedent1,antecedent2,..._consequent"
     *
     * Example:
     * - Pattern: ["laptop"] → "mouse"
     * - ID: "laptop_mouse"
     *
     * @param pattern the basket pattern
     * @return unique pattern ID
     */
    public static String generatePatternId(BasketPattern pattern) {
        String antecedentsStr = String.join(",", pattern.getAntecedents());
        return antecedentsStr + "_" + pattern.getConsequent();
    }

    /**
     * Check if pattern already exists in broadcast state
     *
     * @param existingPattern pattern currently in state
     * @param newPattern new pattern to compare
     * @return true if should update (new pattern has higher confidence)
     */
    public static boolean shouldUpdatePattern(BasketPattern existingPattern, BasketPattern newPattern) {
        if (existingPattern == null) {
            return true; // Always add new patterns
        }

        // Update if new pattern has higher confidence
        return newPattern.getConfidence() > existingPattern.getConfidence();
    }

    // Private constructor to prevent instantiation (utility class)
    private PatternBroadcastState() {}
}
