package com.ververica.composable_job.flink.recommendations.ml;

import deepnetts.core.DeepNetts;
import deepnetts.data.TabularDataSet;
import deepnetts.net.FeedForwardNetwork;
import deepnetts.net.layers.activation.ActivationType;
import deepnetts.net.loss.LossType;
import deepnetts.net.train.BackpropagationTrainer;
import deepnetts.net.train.opt.OptimizerType;
import deepnetts.util.Tensor;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * ML-based product recommender using DeepNetts
 * Implements:
 * - Logistic regression for next-item prediction
 * - Collaborative filtering
 * - Association rule mining
 * - Online learning capabilities
 */
public class ProductRecommender implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    // Product embeddings and features
    private final Map<String, float[]> productEmbeddings = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Double>> productSimilarity = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> productAssociations = new ConcurrentHashMap<>();
    
    // Basket patterns for association rules
    private final List<BasketPattern> patterns = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Integer> itemFrequency = new ConcurrentHashMap<>();
    private final Map<Set<String>, Integer> itemsetFrequency = new ConcurrentHashMap<>();
    
    // Model parameters
    private transient FeedForwardNetwork model;
    private final int embeddingSize = 32;
    private final double learningRate = 0.01;
    private final double minSupport = 0.01;
    private final double minConfidence = 0.3;
    
    // Feature dimensions
    private static final int INPUT_SIZE = 100;  // Max basket size * embedding size
    private static final int HIDDEN_SIZE = 64;
    private static final int OUTPUT_SIZE = 50;  // Top K products to predict
    
    public ProductRecommender() {
        initializeModel();
        initializeEmbeddings();
    }
    
    /**
     * Initialize the neural network model
     */
    private void initializeModel() {
        try {
            FeedForwardNetwork.Builder builder = FeedForwardNetwork.builder();
            builder.addInputLayer(INPUT_SIZE);
            builder.addFullyConnectedLayer(HIDDEN_SIZE, ActivationType.RELU);
            builder.addFullyConnectedLayer(HIDDEN_SIZE / 2, ActivationType.RELU);
            builder.addOutputLayer(OUTPUT_SIZE, ActivationType.SIGMOID);
            
            model = builder.build();
            
            BackpropagationTrainer trainer = model.getTrainer();
            trainer.setMaxEpochs(100);
            trainer.setLearningRate((float) learningRate);
            trainer.setOptimizer(OptimizerType.ADAM);
            trainer.setLossFunction(LossType.CROSS_ENTROPY);
        } catch (Exception e) {
            // Fallback to rule-based if DeepNetts not available
            model = null;
        }
    }
    
    /**
     * Initialize product embeddings with random values
     */
    private void initializeEmbeddings() {
        Random random = new Random(42);
        for (int i = 0; i < 500; i++) { // Pre-initialize for common products
            String productId = "PROD_" + String.format("%04d", i);
            float[] embedding = new float[embeddingSize];
            for (int j = 0; j < embeddingSize; j++) {
                embedding[j] = (float) (random.nextGaussian() * 0.1);
            }
            productEmbeddings.put(productId, embedding);
        }
    }
    
    /**
     * Recommend next items based on current basket
     */
    public List<String> recommendNextItems(List<String> currentBasket, Map<String, Integer> frequentItems, int topK) {
        if (currentBasket == null || currentBasket.isEmpty()) {
            return getPopularItems(topK);
        }
        
        // Use neural network if available
        if (model != null) {
            return neuralRecommendation(currentBasket, topK);
        }
        
        // Fallback to association rules
        return associationRuleRecommendation(currentBasket, frequentItems, topK);
    }
    
    /**
     * Neural network based recommendation
     */
    private List<String> neuralRecommendation(List<String> basket, int topK) {
        try {
            // Convert basket to feature vector
            float[] features = basketToFeatures(basket);
            Tensor input = Tensor.create(1, features.length, features);
            
            // Forward pass
            Tensor output = model.forward(input);
            float[] scores = output.getValues();
            
            // Get top K products
            return getTopProducts(scores, basket, topK);
        } catch (Exception e) {
            // Fallback to association rules
            return associationRuleRecommendation(basket, itemFrequency, topK);
        }
    }
    
    /**
     * Association rule based recommendation
     */
    private List<String> associationRuleRecommendation(List<String> basket, Map<String, Integer> frequentItems, int topK) {
        Map<String, Double> scores = new HashMap<>();
        Set<String> basketSet = new HashSet<>(basket);
        
        // Find matching patterns
        for (BasketPattern pattern : patterns) {
            if (basketSet.containsAll(pattern.getAntecedents())) {
                String consequent = pattern.getConsequent();
                if (!basketSet.contains(consequent)) {
                    scores.merge(consequent, pattern.getConfidence(), Double::sum);
                }
            }
        }
        
        // Add frequent items
        for (Map.Entry<String, Integer> entry : frequentItems.entrySet()) {
            if (!basketSet.contains(entry.getKey())) {
                scores.merge(entry.getKey(), entry.getValue() / 100.0, Double::sum);
            }
        }
        
        // Sort and return top K
        return scores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    /**
     * Get contextual recommendations based on current product view
     */
    public List<String> getContextualRecommendations(String productId, List<String> viewHistory, int topK) {
        Set<String> similar = productAssociations.getOrDefault(productId, new HashSet<>());
        Map<String, Double> scores = new HashMap<>();
        
        // Score based on similarity
        Map<String, Double> similarities = productSimilarity.getOrDefault(productId, new HashMap<>());
        scores.putAll(similarities);
        
        // Boost based on view history
        for (String viewed : viewHistory) {
            Map<String, Double> viewedSimilar = productSimilarity.getOrDefault(viewed, new HashMap<>());
            for (Map.Entry<String, Double> entry : viewedSimilar.entrySet()) {
                scores.merge(entry.getKey(), entry.getValue() * 0.5, Double::sum);
            }
        }
        
        // Remove already viewed items
        viewHistory.forEach(scores::remove);
        scores.remove(productId);
        
        return scores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    /**
     * Get cross-sell items based on purchased products
     */
    public List<String> getCrossSellItems(List<String> purchasedProducts, int topK) {
        Map<String, Double> scores = new HashMap<>();
        Set<String> purchased = new HashSet<>(purchasedProducts);
        
        // Find complementary products
        for (String product : purchasedProducts) {
            Set<String> associated = productAssociations.getOrDefault(product, new HashSet<>());
            for (String assoc : associated) {
                if (!purchased.contains(assoc)) {
                    scores.merge(assoc, 1.0, Double::sum);
                }
            }
        }
        
        // Use patterns for cross-sell
        for (BasketPattern pattern : patterns) {
            Set<String> antecedents = new HashSet<>(pattern.getAntecedents());
            if (purchased.containsAll(antecedents)) {
                String consequent = pattern.getConsequent();
                if (!purchased.contains(consequent)) {
                    scores.merge(consequent, pattern.getConfidence() * pattern.getSupport(), Double::sum);
                }
            }
        }
        
        return scores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    /**
     * Update model with new purchase data (online learning)
     */
    public void updateModel(List<String> basket, String purchased) {
        // Update frequencies
        itemFrequency.merge(purchased, 1, Integer::sum);
        for (String item : basket) {
            itemFrequency.merge(item, 1, Integer::sum);
        }
        
        // Update associations
        for (String item : basket) {
            productAssociations.computeIfAbsent(item, k -> new HashSet<>()).add(purchased);
            productAssociations.computeIfAbsent(purchased, k -> new HashSet<>()).add(item);
        }
        
        // Update itemset frequency
        Set<String> itemset = new HashSet<>(basket);
        itemset.add(purchased);
        itemsetFrequency.merge(itemset, 1, Integer::sum);
        
        // Mine new association rules if enough data
        if (patterns.size() % 100 == 0) {
            mineAssociationRules();
        }
        
        // Update neural network if available
        if (model != null) {
            trainOnlineStep(basket, purchased);
        }
    }
    
    /**
     * Add new training pattern
     */
    public void addTrainingPattern(BasketPattern pattern) {
        patterns.add(pattern);
        
        // Update frequencies
        for (String item : pattern.getAntecedents()) {
            itemFrequency.merge(item, 1, Integer::sum);
        }
        itemFrequency.merge(pattern.getConsequent(), 1, Integer::sum);
        
        // Periodically mine rules
        if (patterns.size() % 50 == 0) {
            mineAssociationRules();
        }
    }
    
    /**
     * Mine association rules from patterns
     */
    private void mineAssociationRules() {
        int totalTransactions = patterns.size();
        if (totalTransactions == 0) return;
        
        // Calculate support for itemsets
        Map<Set<String>, Double> support = new HashMap<>();
        for (Map.Entry<Set<String>, Integer> entry : itemsetFrequency.entrySet()) {
            double sup = entry.getValue() / (double) totalTransactions;
            if (sup >= minSupport) {
                support.put(entry.getKey(), sup);
            }
        }
        
        // Generate rules
        for (Map.Entry<Set<String>, Double> entry : support.entrySet()) {
            Set<String> itemset = entry.getKey();
            if (itemset.size() >= 2) {
                generateRules(itemset, entry.getValue());
            }
        }
    }
    
    /**
     * Generate association rules from an itemset
     */
    private void generateRules(Set<String> itemset, double itemsetSupport) {
        for (String consequent : itemset) {
            Set<String> antecedents = new HashSet<>(itemset);
            antecedents.remove(consequent);
            
            int antecedentCount = itemsetFrequency.getOrDefault(antecedents, 0);
            if (antecedentCount > 0) {
                double confidence = itemsetSupport * patterns.size() / antecedentCount;
                if (confidence >= minConfidence) {
                    BasketPattern pattern = new BasketPattern();
                    pattern.setAntecedents(new ArrayList<>(antecedents));
                    pattern.setConsequent(consequent);
                    pattern.setSupport(itemsetSupport);
                    pattern.setConfidence(confidence);
                    pattern.setLift(confidence / (itemFrequency.getOrDefault(consequent, 1) / (double) patterns.size()));
                    
                    patterns.add(pattern);
                }
            }
        }
    }
    
    /**
     * Convert basket to feature vector
     */
    private float[] basketToFeatures(List<String> basket) {
        float[] features = new float[INPUT_SIZE];
        int idx = 0;
        
        for (String product : basket) {
            if (idx >= INPUT_SIZE / embeddingSize) break;
            
            float[] embedding = productEmbeddings.get(product);
            if (embedding != null) {
                System.arraycopy(embedding, 0, features, idx * embeddingSize, embeddingSize);
                idx++;
            }
        }
        
        return features;
    }
    
    /**
     * Get top products from scores
     */
    private List<String> getTopProducts(float[] scores, List<String> exclude, int topK) {
        Set<String> excludeSet = new HashSet<>(exclude);
        List<Map.Entry<String, Float>> scored = new ArrayList<>();
        
        List<String> allProducts = new ArrayList<>(productEmbeddings.keySet());
        for (int i = 0; i < Math.min(scores.length, allProducts.size()); i++) {
            String product = allProducts.get(i);
            if (!excludeSet.contains(product)) {
                scored.add(new AbstractMap.SimpleEntry<>(product, scores[i]));
            }
        }
        
        return scored.stream()
            .sorted(Map.Entry.<String, Float>comparingByValue().reversed())
            .limit(topK)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    /**
     * Get popular items as fallback
     */
    private List<String> getPopularItems(int topK) {
        return itemFrequency.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(topK)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    /**
     * Online training step for neural network
     */
    private void trainOnlineStep(List<String> basket, String target) {
        if (model == null) return;
        
        try {
            // Prepare training data
            float[] input = basketToFeatures(basket);
            float[] output = new float[OUTPUT_SIZE];
            
            // Set target output
            List<String> products = new ArrayList<>(productEmbeddings.keySet());
            int targetIdx = products.indexOf(target);
            if (targetIdx >= 0 && targetIdx < OUTPUT_SIZE) {
                output[targetIdx] = 1.0f;
            }
            
            // Create mini-batch
            TabularDataSet.Builder dataSetBuilder = new TabularDataSet.Builder(INPUT_SIZE, OUTPUT_SIZE);
            dataSetBuilder.addRow(input, output);
            TabularDataSet dataSet = dataSetBuilder.build();
            
            // Train one step
            model.getTrainer().train(dataSet);
        } catch (Exception e) {
            // Log error but continue
        }
    }
}