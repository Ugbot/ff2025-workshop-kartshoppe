package com.ververica.composable_job.quarkus.kafka.streams;

import com.ververica.composable_job.model.ProcessingEvent;
import com.ververica.composable_job.model.ecommerce.Product;
import com.ververica.composable_job.quarkus.websocket.WebsocketEmitter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@Startup
public class ProductCacheService {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Map<String, Product> productCache = new ConcurrentHashMap<>();
    private KafkaStreams streams;
    private ReadOnlyKeyValueStore<String, Product> productStore;
    
    @Inject
    WebsocketEmitter websocketEmitter;
    
    public void initializeCache(KafkaStreams kafkaStreams) {
        this.streams = kafkaStreams;
        
        // Wait for streams to be ready
        streams.setStateListener((newState, oldState) -> {
            if (newState == KafkaStreams.State.RUNNING) {
                try {
                    productStore = streams.store(
                        StoreQueryParameters.fromNameAndType(
                            "products-cache",
                            QueryableStoreTypes.keyValueStore()
                        )
                    );
                    Log.info("Product cache store initialized");
                    loadInitialProducts();
                } catch (Exception e) {
                    Log.error("Failed to initialize product store", e);
                }
            }
        });
    }
    
    private void loadInitialProducts() {
        // Load initial product catalog
        initializeDefaultProducts();
        
        // Sync with store if available
        if (productStore != null) {
            try {
                productStore.all().forEachRemaining(kv -> {
                    productCache.put(kv.key, kv.value);
                });
                Log.infof("Loaded %d products from KStreams cache", productCache.size());
            } catch (Exception e) {
                Log.warn("Could not load from KStreams cache, using defaults", e);
            }
        }
    }
    
    @Scheduled(every = "30s")
    public void syncCacheToClients() {
        if (productCache.isEmpty()) return;
        
        try {
            // Send cache sync event to all connected clients
            Map<String, Object> cacheSync = new HashMap<>();
            cacheSync.put("products", new ArrayList<>(productCache.values()));
            cacheSync.put("timestamp", System.currentTimeMillis());
            
            ProcessingEvent<Map<String, Object>> syncEvent = new ProcessingEvent<>(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                null,
                null,
                ProcessingEvent.Type.PRODUCT_UPDATE,
                cacheSync
            );
            
            String json = MAPPER.writeValueAsString(syncEvent);
            websocketEmitter.emmit(json);
            
            Log.debugf("Synced %d products to clients", productCache.size());
        } catch (Exception e) {
            Log.error("Failed to sync cache to clients", e);
        }
    }
    
    public void updateProduct(Product product) {
        productCache.put(product.productId, product);
        
        // Send individual product update
        try {
            ProcessingEvent<Product> updateEvent = new ProcessingEvent<>(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                product.productId,
                null,
                ProcessingEvent.Type.PRODUCT_UPDATE,
                product
            );
            
            websocketEmitter.emmit(MAPPER.writeValueAsString(updateEvent));
        } catch (Exception e) {
            Log.error("Failed to send product update", e);
        }
    }
    
    public Product getProduct(String productId) {
        // Try cache first
        Product product = productCache.get(productId);
        
        // Try KStreams store if not in cache
        if (product == null && productStore != null) {
            try {
                product = productStore.get(productId);
                if (product != null) {
                    productCache.put(productId, product);
                }
            } catch (Exception e) {
                Log.warn("Failed to query KStreams store", e);
            }
        }
        
        return product;
    }
    
    public Collection<Product> getAllProducts() {
        return productCache.values();
    }
    
    public List<Product> getProductsByCategory(String category) {
        return productCache.values().stream()
            .filter(p -> p.category.equalsIgnoreCase(category))
            .toList();
    }
    
    public List<Product> searchProducts(String query) {
        String searchLower = query.toLowerCase();
        return productCache.values().stream()
            .filter(p -> p.name.toLowerCase().contains(searchLower) ||
                        p.description.toLowerCase().contains(searchLower) ||
                        p.tags.stream().anyMatch(t -> t.toLowerCase().contains(searchLower)))
            .toList();
    }
    
    private void initializeDefaultProducts() {
        String[] categories = {"Electronics", "Fashion", "Home & Garden", "Sports", "Books", "Toys", "Beauty", "Food & Grocery"};
        String[] brands = {"TechPro", "StyleCraft", "HomeEssentials", "SportMax", "BookWorm", 
                          "ToyLand", "BeautyPlus", "GourmetKitchen", "EcoLife", "PremiumCo"};
        String[][] productNames = {
            {"Wireless Headphones", "Smart Watch", "Laptop", "Tablet", "Camera"},
            {"Designer Jacket", "Running Shoes", "Leather Bag", "Sunglasses", "Watch"},
            {"Coffee Maker", "Air Purifier", "Smart Light", "Vacuum Cleaner", "Blender"},
            {"Yoga Mat", "Dumbbells", "Bicycle", "Tennis Racket", "Swimming Goggles"},
            {"Bestseller Novel", "Cookbook", "Travel Guide", "Science Fiction", "Biography"}
        };
        
        Random random = new Random();
        int productId = 1;
        
        for (int cat = 0; cat < 5; cat++) {
            for (String productName : productNames[cat]) {
                for (int variant = 1; variant <= 4; variant++) {
                    String id = "prod_" + productId++;
                    String category = categories[cat];
                    String brand = brands[random.nextInt(brands.length)];
                    
                    Product product = new Product(
                        id,
                        brand + " " + productName + " " + (variant > 1 ? "v" + variant : "Pro"),
                        "Experience premium quality with our " + productName.toLowerCase() + 
                        ". Designed for modern lifestyle with cutting-edge features and exceptional build quality.",
                        50 + random.nextDouble() * 950,
                        category,
                        String.format("https://source.unsplash.com/600x400/?%s,%s", 
                                    category.replace(" ", ""), productName.replace(" ", "")),
                        random.nextInt(100) + 1,
                        Arrays.asList("bestseller", "premium", category.toLowerCase().replace(" & ", "-")),
                        3.5 + random.nextDouble() * 1.5,
                        random.nextInt(1000) + 10
                    );
                    
                    productCache.put(id, product);
                }
            }
        }
        
        Log.infof("Initialized %d default products", productCache.size());
    }
}