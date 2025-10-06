package com.ververica.composable_job.flink.recommendations.shared.config;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for the Basket Analysis & Recommendation Job.
 *
 * PATTERN: Configuration Management
 * Provides centralized configuration with builder pattern and environment variable support.
 *
 * Example usage:
 * <pre>{@code
 * // From environment variables (recommended)
 * BasketConfig config = BasketConfig.fromEnvironment();
 *
 * // Custom configuration
 * BasketConfig config = BasketConfig.builder()
 *     .withKafkaBootstrapServers("localhost:19092")
 *     .withParallelism(4)
 *     .withCheckpointInterval(30000L)
 *     .build();
 * }</pre>
 *
 * @see com.ververica.composable_job.flink.inventory.shared.config.InventoryConfig
 */
public class BasketConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    // Kafka Configuration
    private final String kafkaBootstrapServers;
    private final String kafkaGroupId;
    private final Map<String, String> kafkaProperties;

    // Topic Names
    private final String shoppingEventsTopic;
    private final String cartEventsTopic;
    private final String recommendationsTopic;
    private final String patternsTopic;
    private final String websocketFanoutTopic;

    // Checkpoint Configuration
    private final long checkpointInterval;
    private final String checkpointPath;

    // Parallelism Settings
    private final int parallelism;

    // Job Settings
    private final String jobName;

    // Paimon Configuration
    private final String paimonWarehouse;

    private BasketConfig(Builder builder) {
        this.kafkaBootstrapServers = builder.kafkaBootstrapServers;
        this.kafkaGroupId = builder.kafkaGroupId;
        this.kafkaProperties = new HashMap<>(builder.kafkaProperties);
        this.shoppingEventsTopic = builder.shoppingEventsTopic;
        this.cartEventsTopic = builder.cartEventsTopic;
        this.recommendationsTopic = builder.recommendationsTopic;
        this.patternsTopic = builder.patternsTopic;
        this.websocketFanoutTopic = builder.websocketFanoutTopic;
        this.checkpointInterval = builder.checkpointInterval;
        this.checkpointPath = builder.checkpointPath;
        this.parallelism = builder.parallelism;
        this.jobName = builder.jobName;
        this.paimonWarehouse = builder.paimonWarehouse;
    }

    /**
     * Creates a new builder with default values.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a BasketConfig from environment variables.
     *
     * Environment variables:
     * - KAFKA_BOOTSTRAP_SERVERS (default: localhost:19092)
     * - KAFKA_GROUP_ID (default: basket-analysis)
     * - CHECKPOINT_INTERVAL_MS (default: 30000)
     * - PARALLELISM (default: 4)
     * - PAIMON_WAREHOUSE (default: /tmp/paimon)
     */
    public static BasketConfig fromEnvironment() {
        return builder()
            .withKafkaBootstrapServers(getEnv("KAFKA_BOOTSTRAP_SERVERS", "localhost:19092"))
            .withKafkaGroupId(getEnv("KAFKA_GROUP_ID", "basket-analysis"))
            .withCheckpointInterval(Long.parseLong(getEnv("CHECKPOINT_INTERVAL_MS", "30000")))
            .withParallelism(Integer.parseInt(getEnv("PARALLELISM", "4")))
            .withPaimonWarehouse(getEnv("PAIMON_WAREHOUSE", "/tmp/paimon"))
            .build();
    }

    /**
     * Validates the configuration.
     *
     * @throws IllegalStateException if configuration is invalid
     */
    public void validate() {
        if (kafkaBootstrapServers == null || kafkaBootstrapServers.trim().isEmpty()) {
            throw new IllegalStateException("Kafka bootstrap servers cannot be null or empty");
        }
        if (checkpointInterval <= 0) {
            throw new IllegalStateException("Checkpoint interval must be positive");
        }
        if (parallelism <= 0) {
            throw new IllegalStateException("Parallelism must be positive");
        }
    }

    // Getters
    public String getKafkaBootstrapServers() {
        return kafkaBootstrapServers;
    }

    public String getKafkaGroupId() {
        return kafkaGroupId;
    }

    public Map<String, String> getKafkaProperties() {
        return new HashMap<>(kafkaProperties);
    }

    public String getShoppingEventsTopic() {
        return shoppingEventsTopic;
    }

    public String getCartEventsTopic() {
        return cartEventsTopic;
    }

    public String getRecommendationsTopic() {
        return recommendationsTopic;
    }

    public String getPatternsTopic() {
        return patternsTopic;
    }

    public String getWebsocketFanoutTopic() {
        return websocketFanoutTopic;
    }

    public long getCheckpointInterval() {
        return checkpointInterval;
    }

    public String getCheckpointPath() {
        return checkpointPath;
    }

    public int getParallelism() {
        return parallelism;
    }

    public String getJobName() {
        return jobName;
    }

    public String getPaimonWarehouse() {
        return paimonWarehouse;
    }

    @Override
    public String toString() {
        return "BasketConfig{" +
               "kafkaBootstrapServers='" + kafkaBootstrapServers + '\'' +
               ", kafkaGroupId='" + kafkaGroupId + '\'' +
               ", checkpointInterval=" + checkpointInterval +
               ", parallelism=" + parallelism +
               ", jobName='" + jobName + '\'' +
               ", paimonWarehouse='" + paimonWarehouse + '\'' +
               '}';
    }

    // Helper methods
    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Builder for BasketConfig with fluent API.
     */
    public static class Builder {
        // Defaults
        private String kafkaBootstrapServers = "localhost:19092";
        private String kafkaGroupId = "basket-analysis";
        private Map<String, String> kafkaProperties = new HashMap<>();

        // Topic names
        private String shoppingEventsTopic = "ecommerce-events";
        private String cartEventsTopic = "shopping-cart-events";
        private String recommendationsTopic = "product-recommendations";
        private String patternsTopic = "basket-patterns";
        private String websocketFanoutTopic = "websocket_fanout";

        // Checkpoint configuration
        private long checkpointInterval = 30000L; // 30 seconds
        private String checkpointPath = "file:///tmp/flink-checkpoints-basket";

        // Parallelism
        private int parallelism = 4;

        // Job settings
        private String jobName = "Basket Analysis & Recommendation Job";

        // Paimon
        private String paimonWarehouse = "/tmp/paimon";

        private Builder() {}

        public Builder withKafkaBootstrapServers(String kafkaBootstrapServers) {
            this.kafkaBootstrapServers = Objects.requireNonNull(kafkaBootstrapServers);
            return this;
        }

        public Builder withKafkaGroupId(String kafkaGroupId) {
            this.kafkaGroupId = Objects.requireNonNull(kafkaGroupId);
            return this;
        }

        public Builder withKafkaProperty(String key, String value) {
            this.kafkaProperties.put(
                Objects.requireNonNull(key),
                Objects.requireNonNull(value)
            );
            return this;
        }

        public Builder withShoppingEventsTopic(String topic) {
            this.shoppingEventsTopic = Objects.requireNonNull(topic);
            return this;
        }

        public Builder withCartEventsTopic(String topic) {
            this.cartEventsTopic = Objects.requireNonNull(topic);
            return this;
        }

        public Builder withRecommendationsTopic(String topic) {
            this.recommendationsTopic = Objects.requireNonNull(topic);
            return this;
        }

        public Builder withPatternsTopic(String topic) {
            this.patternsTopic = Objects.requireNonNull(topic);
            return this;
        }

        public Builder withWebsocketFanoutTopic(String topic) {
            this.websocketFanoutTopic = Objects.requireNonNull(topic);
            return this;
        }

        public Builder withCheckpointInterval(long checkpointInterval) {
            if (checkpointInterval <= 0) {
                throw new IllegalArgumentException("checkpointInterval must be positive");
            }
            this.checkpointInterval = checkpointInterval;
            return this;
        }

        public Builder withCheckpointPath(String checkpointPath) {
            this.checkpointPath = Objects.requireNonNull(checkpointPath);
            return this;
        }

        public Builder withParallelism(int parallelism) {
            if (parallelism <= 0) {
                throw new IllegalArgumentException("parallelism must be positive");
            }
            this.parallelism = parallelism;
            return this;
        }

        public Builder withJobName(String jobName) {
            this.jobName = Objects.requireNonNull(jobName);
            return this;
        }

        public Builder withPaimonWarehouse(String paimonWarehouse) {
            this.paimonWarehouse = Objects.requireNonNull(paimonWarehouse);
            return this;
        }

        public BasketConfig build() {
            BasketConfig config = new BasketConfig(this);
            config.validate();
            return config;
        }
    }
}
