package com.ververica.composable_job.flink.recommendations.shared.model;

import java.io.Serializable;

/**
 * Represents an item in a shopping basket.
 *
 * Used by:
 * - BasketTrackerFunction to track items added to cart
 * - PatternMinerFunction to analyze basket contents
 * - Association rule mining for recommendations
 */
public class BasketItem implements Serializable {

    private static final long serialVersionUID = 1L;

    public String productId;
    public String productName;
    public String category;
    public Double price;
    public int quantity;
    public long addedAt;

    public BasketItem() {}

    public BasketItem(String productId, String productName, String category,
                     Double price, int quantity, long addedAt) {
        this.productId = productId;
        this.productName = productName;
        this.category = category;
        this.price = price;
        this.quantity = quantity;
        this.addedAt = addedAt;
    }

    @Override
    public String toString() {
        return String.format("BasketItem{id=%s, name=%s, category=%s, price=%.2f, qty=%d}",
            productId, productName, category, price, quantity);
    }
}
