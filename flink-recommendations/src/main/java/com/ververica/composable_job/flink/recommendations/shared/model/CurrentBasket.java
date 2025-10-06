package com.ververica.composable_job.flink.recommendations.shared.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the current basket state for recommendation generation.
 *
 * PATTERN: Keyed State (in RecommendationGeneratorFunction)
 * Stores products and view history to generate context-aware recommendations.
 *
 * Used by:
 * - RecommendationGeneratorFunction to track current session context
 * - Basket-based recommendation logic
 * - View-based recommendation logic
 */
public class CurrentBasket implements Serializable {

    private static final long serialVersionUID = 1L;

    public String sessionId;
    public String userId;
    public List<String> products = new ArrayList<>();
    public List<String> viewHistory = new ArrayList<>();

    public CurrentBasket() {}

    public CurrentBasket(String sessionId, String userId) {
        this.sessionId = sessionId;
        this.userId = userId;
    }

    @Override
    public String toString() {
        return String.format("CurrentBasket{session=%s, user=%s, products=%d, views=%d}",
            sessionId, userId, products.size(), viewHistory.size());
    }
}
