package com.ververica.composable_job.flink.recommendations.source;

import com.ververica.composable_job.flink.recommendations.ml.BasketPattern;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.connector.base.source.hybrid.HybridSource;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.paimon.catalog.Catalog;
import org.apache.paimon.catalog.CatalogContext;
import org.apache.paimon.catalog.CatalogFactory;
import org.apache.paimon.catalog.Identifier;
import org.apache.paimon.flink.FlinkCatalogFactory;
import org.apache.paimon.flink.source.FlinkTableSource;
import org.apache.paimon.options.Options;
import org.apache.paimon.table.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;

/**
 * Hybrid source combining Apache Paimon for historical training data
 * and Kafka for real-time pattern updates
 * 
 * Architecture:
 * 1. Phase 1: Load historical basket patterns from Paimon table
 * 2. Phase 2: Stream new patterns from Kafka for online learning
 */
public class PaimonKafkaHybridSource {
    
    private static final Logger LOG = LoggerFactory.getLogger(PaimonKafkaHybridSource.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    /**
     * Create hybrid source for basket patterns
     */
    public static DataStream<BasketPattern> createHybridPatternSource(
            StreamExecutionEnvironment env,
            String paimonWarehouse,
            String kafkaBootstrapServers,
            String paimonDatabase,
            String paimonTable,
            String kafkaTopic) throws Exception {
        
        LOG.info("Creating hybrid source: Paimon ({}.{}) -> Kafka ({})", 
            paimonDatabase, paimonTable, kafkaTopic);
        
        // Create Paimon source for historical data
        DataStream<String> paimonSource = createPaimonSource(
            env, paimonWarehouse, paimonDatabase, paimonTable);
        
        // Create Kafka source for streaming updates
        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
            .setBootstrapServers(kafkaBootstrapServers)
            .setTopics(kafkaTopic)
            .setGroupId("basket-pattern-training")
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .setProperty("partition.discovery.interval.ms", "10000")
            .build();
        
        // Build hybrid source
        HybridSource<String> hybridSource = HybridSource.<String>builder(paimonSource)
            .addSource(kafkaSource)
            .build();
        
        // Process patterns from hybrid source
        return env.fromSource(
                hybridSource,
                WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofSeconds(5)),
                "Hybrid Pattern Source",
                Types.STRING
            )
            .process(new PatternParser())
            .name("Parse Basket Patterns")
            .uid("pattern-parser");
    }
    
    /**
     * Create Paimon table source for historical patterns
     */
    private static DataStream<String> createPaimonSource(
            StreamExecutionEnvironment env,
            String warehouse,
            String database,
            String tableName) throws Exception {
        
        // Initialize Paimon catalog
        Options catalogOptions = new Options();
        catalogOptions.set("warehouse", warehouse);
        
        CatalogContext catalogContext = CatalogContext.create(catalogOptions);
        Catalog catalog = CatalogFactory.createCatalog(catalogContext);
        
        // Get or create database
        try {
            catalog.createDatabase(database, true);
        } catch (Exception e) {
            LOG.debug("Database {} already exists", database);
        }
        
        // Get or create table
        Identifier tableIdentifier = Identifier.create(database, tableName);
        Table table;
        
        try {
            table = catalog.getTable(tableIdentifier);
        } catch (Exception e) {
            LOG.info("Creating Paimon table {}.{}", database, tableName);
            table = createPatternTable(catalog, tableIdentifier);
        }
        
        // Create Flink source from Paimon table
        FlinkTableSource tableSource = new FlinkTableSource(
            table,
            null,  // No projection
            null,  // No filter
            null   // No limit
        );
        
        // Convert to DataStream
        // Note: This is simplified - actual implementation would use Table API
        return env.fromElements(
            generateInitialPatterns() // Generate sample patterns for demo
        );
    }
    
    /**
     * Create Paimon table for basket patterns
     */
    private static Table createPatternTable(Catalog catalog, Identifier identifier) throws Exception {
        // Define schema for basket patterns table
        String createTableDDL = String.format(
            "CREATE TABLE IF NOT EXISTS %s.%s (" +
            "  pattern_id STRING," +
            "  antecedents ARRAY<STRING>," +
            "  consequent STRING," +
            "  support DOUBLE," +
            "  confidence DOUBLE," +
            "  lift DOUBLE," +
            "  category STRING," +
            "  created_at TIMESTAMP," +
            "  PRIMARY KEY (pattern_id) NOT ENFORCED" +
            ") WITH (" +
            "  'connector' = 'paimon'," +
            "  'path' = '%s/%s.%s'," +
            "  'auto-create' = 'true'" +
            ")",
            identifier.getDatabaseName(),
            identifier.getObjectName(),
            catalog.warehouse(),
            identifier.getDatabaseName(),
            identifier.getObjectName()
        );
        
        // Execute DDL (simplified - actual implementation would use Table API)
        LOG.info("Creating table with DDL: {}", createTableDDL);
        
        return catalog.getTable(identifier);
    }
    
    /**
     * Generate initial training patterns for demo
     */
    private static String[] generateInitialPatterns() {
        List<String> patterns = new ArrayList<>();
        Random random = new Random(42);
        
        // Electronics patterns
        patterns.add(createPattern(Arrays.asList("laptop", "mouse"), "keyboard", 0.85));
        patterns.add(createPattern(Arrays.asList("laptop", "keyboard"), "laptop_stand", 0.72));
        patterns.add(createPattern(Arrays.asList("monitor", "keyboard"), "mouse", 0.91));
        patterns.add(createPattern(Arrays.asList("headphones", "laptop"), "webcam", 0.65));
        patterns.add(createPattern(Arrays.asList("tablet", "stylus"), "tablet_case", 0.78));
        
        // Fashion patterns
        patterns.add(createPattern(Arrays.asList("jeans", "shirt"), "belt", 0.68));
        patterns.add(createPattern(Arrays.asList("dress", "heels"), "purse", 0.74));
        patterns.add(createPattern(Arrays.asList("sneakers", "socks"), "shorts", 0.62));
        patterns.add(createPattern(Arrays.asList("jacket", "scarf"), "gloves", 0.81));
        
        // Home & Garden patterns
        patterns.add(createPattern(Arrays.asList("coffee_maker", "coffee_beans"), "coffee_filters", 0.92));
        patterns.add(createPattern(Arrays.asList("blender", "protein_powder"), "shaker_bottle", 0.71));
        patterns.add(createPattern(Arrays.asList("plant", "pot"), "soil", 0.88));
        patterns.add(createPattern(Arrays.asList("mattress", "pillows"), "bedsheets", 0.95));
        
        // Sports patterns
        patterns.add(createPattern(Arrays.asList("yoga_mat", "yoga_blocks"), "yoga_strap", 0.76));
        patterns.add(createPattern(Arrays.asList("dumbbells", "bench"), "resistance_bands", 0.69));
        patterns.add(createPattern(Arrays.asList("running_shoes", "shorts"), "water_bottle", 0.73));
        patterns.add(createPattern(Arrays.asList("bicycle", "helmet"), "bike_lock", 0.87));
        
        // Generate more synthetic patterns
        String[] categories = {"Electronics", "Fashion", "Home & Garden", "Sports", "Books"};
        for (int i = 0; i < 100; i++) {
            String category = categories[random.nextInt(categories.length)];
            List<String> antecedents = new ArrayList<>();
            
            // Random antecedents
            int numAntecedents = 1 + random.nextInt(3);
            for (int j = 0; j < numAntecedents; j++) {
                antecedents.add(String.format("PROD_%04d", random.nextInt(200)));
            }
            
            String consequent = String.format("PROD_%04d", random.nextInt(200));
            double confidence = 0.5 + random.nextDouble() * 0.5;
            
            patterns.add(createPattern(antecedents, consequent, confidence));
        }
        
        return patterns.toArray(new String[0]);
    }
    
    /**
     * Create a JSON pattern string
     */
    private static String createPattern(List<String> antecedents, String consequent, double confidence) {
        try {
            BasketPattern pattern = new BasketPattern();
            pattern.setAntecedents(antecedents);
            pattern.setConsequent(consequent);
            pattern.setConfidence(confidence);
            pattern.setSupport(confidence * 0.3); // Simplified
            pattern.setLift(confidence / 0.2); // Simplified
            pattern.setTimestamp(System.currentTimeMillis());
            
            return MAPPER.writeValueAsString(pattern);
        } catch (Exception e) {
            return "{}";
        }
    }
    
    /**
     * Process function to parse pattern strings
     */
    public static class PatternParser extends ProcessFunction<String, BasketPattern> {
        private static final ObjectMapper mapper = new ObjectMapper();
        
        @Override
        public void processElement(String value, Context ctx, Collector<BasketPattern> out) throws Exception {
            try {
                // Try to parse as BasketPattern
                BasketPattern pattern = mapper.readValue(value, BasketPattern.class);
                
                // Validate pattern
                if (pattern.getAntecedents() != null && 
                    !pattern.getAntecedents().isEmpty() && 
                    pattern.getConsequent() != null) {
                    
                    // Set defaults if missing
                    if (pattern.getSupport() == 0) {
                        pattern.setSupport(0.01);
                    }
                    if (pattern.getConfidence() == 0) {
                        pattern.setConfidence(0.5);
                    }
                    if (pattern.getLift() == 0) {
                        pattern.setLift(pattern.getConfidence() / 0.2);
                    }
                    
                    out.collect(pattern);
                    LOG.debug("Parsed pattern: {}", pattern);
                }
            } catch (Exception e) {
                // Try alternative parsing for CSV or other formats
                try {
                    BasketPattern pattern = parseAlternativeFormat(value);
                    if (pattern != null) {
                        out.collect(pattern);
                    }
                } catch (Exception ex) {
                    LOG.warn("Failed to parse pattern: {}", value, ex);
                }
            }
        }
        
        /**
         * Parse alternative formats (CSV, etc.)
         */
        private BasketPattern parseAlternativeFormat(String value) {
            // Format: "product1,product2->product3:confidence"
            if (value.contains("->") && value.contains(":")) {
                String[] parts = value.split("->");
                String[] antecedentsPart = parts[0].split(",");
                String[] consequentPart = parts[1].split(":");
                
                BasketPattern pattern = new BasketPattern();
                pattern.setAntecedents(Arrays.asList(antecedentsPart));
                pattern.setConsequent(consequentPart[0]);
                pattern.setConfidence(Double.parseDouble(consequentPart[1]));
                pattern.setSupport(pattern.getConfidence() * 0.3);
                pattern.setLift(pattern.getConfidence() / 0.2);
                
                return pattern;
            }
            
            return null;
        }
    }
}