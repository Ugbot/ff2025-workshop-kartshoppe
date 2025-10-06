package com.ververica.composable_job.flink.recommendations.shared.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an active shopping basket being tracked.
 *
 * PATTERN: Keyed State
 * Stored as ValueState keyed by sessionId to track basket progress.
 *
 * Used by:
 * - BasketTrackerFunction to maintain basket metadata
 * - Session timeout detection with timers
 */
public class ActiveBasket implements Serializable {

    private static final long serialVersionUID = 1L;

    public String sessionId;
    public String userId;
    public List<String> viewedProducts = new ArrayList<>();
    public int itemCount = 0;
    public double totalValue = 0;
    public long createdAt;

    public ActiveBasket() {}

    public ActiveBasket(String sessionId, String userId) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.createdAt = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return String.format("ActiveBasket{session=%s, user=%s, items=%d, value=%.2f}",
            sessionId, userId, itemCount, totalValue);
    }
}
