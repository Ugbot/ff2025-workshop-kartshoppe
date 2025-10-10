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
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.util.Collector;
import org.apache.paimon.catalog.Catalog;
import org.apache.paimon.catalog.CatalogContext;
import org.apache.paimon.catalog.CatalogFactory;
import org.apache.paimon.catalog.Identifier;
import org.apache.paimon.data.InternalRow;
import org.apache.paimon.flink.FlinkCatalogFactory;
import org.apache.paimon.flink.source.FlinkTableSource;
import org.apache.paimon.options.Options;
import org.apache.paimon.types.RowType;
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

        // Create Table Environment for Paimon access
        StreamTableEnvironment tEnv = StreamTableEnvironment.create(env);

        // Create Paimon source for historical data using Table API
        DataStream<BasketPattern> paimonPatterns = createPaimonSourceTableAPI(
            env, tEnv, paimonWarehouse, paimonDatabase, paimonTable);

        // Create Kafka source for streaming updates
        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
            .setBootstrapServers(kafkaBootstrapServers)
            .setTopics(kafkaTopic)
            .setGroupId("basket-pattern-training")
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .setProperty("partition.discovery.interval.ms", "10000")
            .build();

        // Parse Kafka patterns
        DataStream<BasketPattern> kafkaPatterns = env.fromSource(
                kafkaSource,
                WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofSeconds(5)),
                "Kafka Pattern Stream"
            )
            .process(new PatternParser())
            .name("Parse Kafka Patterns")
            .uid("kafka-pattern-parser");

        // Union Paimon (historical) and Kafka (live) patterns
        return paimonPatterns.union(kafkaPatterns);
    }

    /**
     * Create Paimon source using Table API (cleaner approach)
     */
    private static DataStream<BasketPattern> createPaimonSourceTableAPI(
            StreamExecutionEnvironment env,
            StreamTableEnvironment tEnv,
            String warehouse,
            String database,
            String tableName) throws Exception {

        try {
            // Create Paimon catalog
            String catalogDDL = String.format(
                "CREATE CATALOG IF NOT EXISTS paimon_catalog WITH (\n" +
                "  'type' = 'paimon',\n" +
                "  'warehouse' = '%s'\n" +
                ")", warehouse);

            tEnv.executeSql(catalogDDL);
            tEnv.executeSql("USE CATALOG paimon_catalog");

            // Check if table exists
            String checkTable = String.format("SHOW TABLES FROM %s LIKE '%s'", database, tableName);
            org.apache.flink.table.api.TableResult result = tEnv.executeSql(checkTable);

            if (!result.collect().hasNext()) {
                LOG.warn("⚠️  Paimon table {}.{} not found", database, tableName);
                LOG.info("   Run ./init-paimon-training-data.sh to create training data");
                LOG.info("   Using fallback sample patterns...");
                return env.fromElements(generateSamplePatterns());
            }

            LOG.info("✅ Found Paimon table: {}.{}", database, tableName);

            // Read table using Table API
            Table table = tEnv.sqlQuery(String.format(
                "SELECT antecedents, consequent, support, confidence, lift, category, user_id, session_id, created_at " +
                "FROM %s.%s", database, tableName
            ));

            // Convert to DataStream<Row> then to BasketPattern
            DataStream<org.apache.flink.types.Row> rows = tEnv.toDataStream(table);

            return rows.map(row -> {
                // Extract fields from Row
                @SuppressWarnings("unchecked")
                List<String> antecedents = (List<String>) row.getField(0);
                String consequent = (String) row.getField(1);
                Double support = (Double) row.getField(2);
                Double confidence = (Double) row.getField(3);
                Double lift = (Double) row.getField(4);
                String category = (String) row.getField(5);
                String userId = (String) row.getField(6);
                String sessionId = (String) row.getField(7);

                // Get timestamp (convert from LocalDateTime if needed)
                long timestamp = System.currentTimeMillis();
                Object createdAtObj = row.getField(8);
                if (createdAtObj != null) {
                    if (createdAtObj instanceof java.time.LocalDateTime) {
                        timestamp = ((java.time.LocalDateTime) createdAtObj)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli();
                    }
                }

                return new BasketPattern(
                    antecedents,
                    consequent,
                    support,
                    confidence,
                    lift,
                    category,
                    userId,
                    sessionId,
                    timestamp
                );
            })
            .name("Convert Paimon Rows to BasketPatterns")
            .uid("paimon-row-to-pattern");

        } catch (Exception e) {
            LOG.warn("⚠️  Failed to read Paimon table: {}", e.getMessage());
            LOG.info("   Using fallback sample patterns...");
            return env.fromElements(generateSamplePatterns());
        }
    }

    /**
     * Generate sample patterns for fallback/demo
     */
    private static BasketPattern[] generateSamplePatterns() {
        List<BasketPattern> patterns = new ArrayList<>();

        // Electronics
        patterns.add(createPatternObject(Arrays.asList("laptop"), "mouse", 0.15, 0.75, 2.1, "electronics"));
        patterns.add(createPatternObject(Arrays.asList("laptop"), "keyboard", 0.12, 0.68, 1.9, "electronics"));
        patterns.add(createPatternObject(Arrays.asList("phone"), "charger", 0.20, 0.85, 2.8, "electronics"));

        // Fashion
        patterns.add(createPatternObject(Arrays.asList("shirt"), "pants", 0.25, 0.72, 2.0, "fashion"));
        patterns.add(createPatternObject(Arrays.asList("dress"), "shoes", 0.18, 0.76, 2.3, "fashion"));

        // Home & Kitchen
        patterns.add(createPatternObject(Arrays.asList("coffee_maker"), "coffee_beans", 0.22, 0.89, 2.7, "home_kitchen"));
        patterns.add(createPatternObject(Arrays.asList("coffee_maker"), "filters", 0.19, 0.81, 2.5, "home_kitchen"));

        // Sports
        patterns.add(createPatternObject(Arrays.asList("yoga_mat"), "yoga_blocks", 0.14, 0.67, 2.0, "sports"));
        patterns.add(createPatternObject(Arrays.asList("bicycle"), "helmet", 0.23, 0.87, 2.8, "sports"));

        LOG.info("📝 Generated {} sample patterns for fallback", patterns.size());

        return patterns.toArray(new BasketPattern[0]);
    }

    /**
     * Create BasketPattern object
     */
    private static BasketPattern createPatternObject(List<String> antecedents, String consequent,
                                                     double support, double confidence, double lift, String category) {
        return new BasketPattern(
            antecedents,
            consequent,
            support,
            confidence,
            lift,
            category,
            null,
            null,
            System.currentTimeMillis()
        );
    }
    
    /**
     * Create Paimon table source for historical patterns
     * @deprecated Use createPaimonSourceTableAPI instead
     */
    @Deprecated
    private static DataStream<BasketPattern> createPaimonSource(
            StreamExecutionEnvironment env,
            String warehouse,
            String database,
            String tableName) throws Exception {

        // Initialize Paimon catalog
        Options catalogOptions = new Options();
        catalogOptions.set("warehouse", warehouse);

        CatalogContext catalogContext = CatalogContext.create(catalogOptions);
        Catalog catalog = CatalogFactory.createCatalog(catalogContext);

        // Check if database exists
        try {
            catalog.createDatabase(database, true);
        } catch (Exception e) {
            LOG.debug("Database {} already exists", database);
        }

        // Get table
        Identifier tableIdentifier = Identifier.create(database, tableName);
        org.apache.paimon.table.Table table;

        try {
            table = catalog.getTable(tableIdentifier);
            LOG.info("✅ Found Paimon table: {}.{}", database, tableName);
        } catch (Exception e) {
            LOG.warn("⚠️  Paimon table {}.{} not found - using fallback sample patterns", database, tableName);
            LOG.info("   Run ./init-paimon-training-data.sh to create historical training data");
            // Fallback to sample patterns if table doesn't exist
            return env.fromElements(convertStringPatternsToObjects(generateInitialPatterns()));
        }

        // Note: FlinkTableSource is abstract in Paimon 0.9+
        // Using Table API (createPaimonSourceTableAPI) instead for better compatibility
        // This method is kept for reference but should not be used directly

        LOG.warn("⚠️  createPaimonSource() is deprecated - use createPaimonSourceTableAPI() instead");
        return env.fromElements(generateSamplePatterns());
    }
    
    /**
     * Create Paimon table for basket patterns
     */
    private static org.apache.paimon.table.Table createPatternTable(Catalog catalog, Identifier identifier) throws Exception {
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
     * Convert String patterns to BasketPattern objects
     */
    private static BasketPattern[] convertStringPatternsToObjects(String[] patterns) {
        List<BasketPattern> result = new ArrayList<>();
        for (String json : patterns) {
            try {
                result.add(MAPPER.readValue(json, BasketPattern.class));
            } catch (Exception e) {
                LOG.warn("Failed to parse pattern: {}", json);
            }
        }
        return result.toArray(new BasketPattern[0]);
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

    /**
     * Convert Paimon InternalRow to JSON BasketPattern string
     */
    public static class PaimonRowToJsonConverter implements org.apache.flink.api.common.functions.MapFunction<InternalRow, String> {
        private final RowType rowType;
        private static final ObjectMapper mapper = new ObjectMapper();

        public PaimonRowToJsonConverter(RowType rowType) {
            this.rowType = rowType;
        }

        @Override
        public String map(InternalRow row) throws Exception {
            try {
                // Extract fields from Paimon row based on schema:
                // pattern_id, antecedents, consequent, support, confidence, lift, category, user_id, session_id, created_at, event_time

                // Get field indices
                int antecedentsIdx = rowType.getFieldIndex("antecedents");
                int consequentIdx = rowType.getFieldIndex("consequent");
                int supportIdx = rowType.getFieldIndex("support");
                int confidenceIdx = rowType.getFieldIndex("confidence");
                int liftIdx = rowType.getFieldIndex("lift");
                int categoryIdx = rowType.getFieldIndex("category");
                int userIdIdx = rowType.getFieldIndex("user_id");
                int sessionIdIdx = rowType.getFieldIndex("session_id");
                int createdAtIdx = rowType.getFieldIndex("created_at");

                // Extract antecedents array
                org.apache.paimon.data.InternalArray antecedentsArray =
                    row.getArray(antecedentsIdx);
                List<String> antecedents = new ArrayList<>();
                if (antecedentsArray != null) {
                    for (int i = 0; i < antecedentsArray.size(); i++) {
                        antecedents.add(antecedentsArray.getString(i).toString());
                    }
                }

                // Extract other fields
                String consequent = row.getString(consequentIdx).toString();
                double support = row.getDouble(supportIdx);
                double confidence = row.getDouble(confidenceIdx);
                double lift = row.getDouble(liftIdx);
                String category = row.isNullAt(categoryIdx) ? null : row.getString(categoryIdx).toString();
                String userId = row.isNullAt(userIdIdx) ? null : row.getString(userIdIdx).toString();
                String sessionId = row.isNullAt(sessionIdIdx) ? null : row.getString(sessionIdIdx).toString();

                // Get timestamp (convert Timestamp to millis)
                long timestamp = System.currentTimeMillis();
                if (!row.isNullAt(createdAtIdx)) {
                    org.apache.paimon.data.Timestamp ts = row.getTimestamp(createdAtIdx, 3);
                    timestamp = ts.getMillisecond();
                }

                // Create BasketPattern
                BasketPattern pattern = new BasketPattern(
                    antecedents,
                    consequent,
                    support,
                    confidence,
                    lift,
                    category,
                    userId,
                    sessionId,
                    timestamp
                );

                // Convert to JSON
                return mapper.writeValueAsString(pattern);

            } catch (Exception e) {
                LOG.error("Failed to convert Paimon row to BasketPattern", e);
                // Return minimal valid pattern on error
                BasketPattern fallback = new BasketPattern();
                fallback.setAntecedents(Arrays.asList("unknown"));
                fallback.setConsequent("unknown");
                fallback.setConfidence(0.5);
                fallback.setSupport(0.01);
                fallback.setLift(1.0);
                return mapper.writeValueAsString(fallback);
            }
        }
    }
}