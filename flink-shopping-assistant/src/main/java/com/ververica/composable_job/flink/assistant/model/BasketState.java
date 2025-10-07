package com.ververica.composable_job.flink.assistant.model;

import com.ververica.composable_job.model.ecommerce.Product;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BasketState implements Serializable {
    public String sessionId;
    public Map<String, ProductInfo> items = new HashMap<>();

    public BasketState() {}

    public BasketState(String sessionId) {
        this.sessionId = sessionId;
    }

    public void addItem(String productId, String productName, double price, String category) {
        ProductInfo info = items.getOrDefault(productId, new ProductInfo(productId, productName, price, category));
        info.quantity++;
        items.put(productId, info);
    }

    public void removeItem(String productId) {
        items.remove(productId);
    }

    public void clear() {
        items.clear();
    }

    public int getTotalItemCount() {
        return items.values().stream().mapToInt(p -> p.quantity).sum();
    }

    public double getTotalValue() {
        return items.values().stream().mapToDouble(p -> p.price * p.quantity).sum();
    }

    public List<String> getCategories() {
        return items.values().stream()
            .map(p -> p.category)
            .distinct()
            .collect(Collectors.toList());
    }

    public List<String> getProductNames() {
        return new ArrayList<>(items.values().stream()
            .map(p -> p.name)
            .collect(Collectors.toList()));
    }

    public static class ProductInfo implements Serializable {
        public String productId;
        public String name;
        public double price;
        public String category;
        public int quantity = 0;

        public ProductInfo() {}

        public ProductInfo(String productId, String name, double price, String category) {
            this.productId = productId;
            this.name = name;
            this.price = price;
            this.category = category;
        }
    }
}
