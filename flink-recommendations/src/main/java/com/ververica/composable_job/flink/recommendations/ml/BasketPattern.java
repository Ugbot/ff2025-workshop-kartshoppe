package com.ververica.composable_job.flink.recommendations.ml;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Represents an association rule pattern for basket analysis
 * Format: {antecedents} => {consequent}
 * Example: {bread, butter} => {milk} with confidence 0.8
 */
public class BasketPattern implements Serializable {
    
    private List<String> antecedents;  // Items in basket (IF)
    private String consequent;          // Predicted item (THEN)
    private double support;             // Frequency of pattern
    private double confidence;          // P(consequent | antecedents)
    private double lift;                // Confidence / P(consequent)
    private long timestamp;
    private String userId;
    private String sessionId;
    private String category;
    
    public BasketPattern() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public BasketPattern(List<String> antecedents, String consequent, double confidence) {
        this.antecedents = antecedents;
        this.consequent = consequent;
        this.confidence = confidence;
        this.timestamp = System.currentTimeMillis();
    }
    
    // Getters and setters
    public List<String> getAntecedents() {
        return antecedents;
    }
    
    public void setAntecedents(List<String> antecedents) {
        this.antecedents = antecedents;
    }
    
    public String getConsequent() {
        return consequent;
    }
    
    public void setConsequent(String consequent) {
        this.consequent = consequent;
    }
    
    public double getSupport() {
        return support;
    }
    
    public void setSupport(double support) {
        this.support = support;
    }
    
    public double getConfidence() {
        return confidence;
    }
    
    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
    
    public double getLift() {
        return lift;
    }
    
    public void setLift(double lift) {
        this.lift = lift;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    /**
     * Calculate the interestingness of this pattern
     * Higher values indicate more interesting/useful patterns
     */
    public double getInterestingness() {
        // Combine multiple metrics for interestingness
        double score = 0.0;
        
        // Confidence is important
        score += confidence * 0.4;
        
        // Lift > 1 indicates positive correlation
        if (lift > 1) {
            score += Math.min(lift / 10, 0.3); // Cap contribution at 0.3
        }
        
        // Support indicates frequency
        score += Math.min(support * 10, 0.3); // Cap contribution at 0.3
        
        return score;
    }
    
    /**
     * Check if this pattern is significant
     */
    public boolean isSignificant(double minSupport, double minConfidence, double minLift) {
        return support >= minSupport && 
               confidence >= minConfidence && 
               lift >= minLift;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BasketPattern that = (BasketPattern) o;
        return Objects.equals(antecedents, that.antecedents) &&
               Objects.equals(consequent, that.consequent);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(antecedents, consequent);
    }
    
    @Override
    public String toString() {
        return String.format("%s => %s (sup=%.3f, conf=%.3f, lift=%.3f)",
            antecedents, consequent, support, confidence, lift);
    }
}