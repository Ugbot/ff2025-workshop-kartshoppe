package com.ververica.composable_job.flink.recommendations.shared.model;

import java.io.Serializable;
import java.util.List;

/**
 * Represents a completed shopping basket (purchase).
 *
 * Emitted when:
 * - User completes checkout/purchase
 * - Basket contains items
 *
 * Used by:
 * - BasketTrackerFunction (emits when purchase detected)
 * - PatternMinerFunction (consumes for association rule mining)
 * - ML training pipeline (historical basket analysis)
 *
 * PATTERN FLOW:
 * EcommerceEvent → BasketTrackerFunction → BasketCompletion → PatternMinerFunction
 */
public class BasketCompletion implements Serializable {

    private static final long serialVersionUID = 1L;

    public String sessionId;
    public String userId;
    public List<BasketItem> items;
    public double totalValue;
    public long completionTime;
    public int itemCount;

    public BasketCompletion() {}

    @Override
    public String toString() {
        return String.format("BasketCompletion{session=%s, user=%s, items=%d, value=%.2f, time=%d}",
            sessionId, userId, itemCount, totalValue, completionTime);
    }
}
