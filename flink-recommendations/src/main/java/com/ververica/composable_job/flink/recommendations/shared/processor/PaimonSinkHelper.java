package com.ververica.composable_job.flink.recommendations.shared.processor;

import com.ververica.composable_job.flink.recommendations.ml.BasketPattern;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.table.api.Schema;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableDescriptor;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.paimon.catalog.Catalog;
import org.apache.paimon.catalog.CatalogContext;
import org.apache.paimon.catalog.CatalogFactory;
import org.apache.paimon.catalog.Identifier;
import org.apache.paimon.options.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.flink.table.api.Expressions.$;

/**
 * Helper class for writing basket patterns to Apache Paimon.
 *
 * ARCHITECTURE:
 * Basket Patterns (real-time) → Paimon Table (historical storage)
 *                              → Used for ML model training
 *                              → Time-travel queries
 *                              → Analytics
 *
 * TABLE SCHEMA:
 * - pattern_id: Unique identifier (antecedents_consequent)
 * - antecedents: Array of product IDs in the pattern
 * - consequent: Product ID that is recommended
 * - support: How often pattern appears (0.0-1.0)
 * - confidence: Probability of consequent given antecedents (0.0-1.0)
 * - lift: Strength of association (>1.0 = positive correlation)
 * - category: Product category
 * - user_id: User who generated this pattern (optional)
 * - session_id: Session ID (optional)
 * - created_at: Timestamp when pattern was mined
 * - event_time: Event time for time-travel queries
 *
 * PAIMON FEATURES:
 * - Automatic compaction
 * - Time-travel capabilities
 * - Batch + Stream unified storage
 * - Efficient updates (primary key: pattern_id)
 *
 * @see com.ververica.composable_job.flink.inventory.shared.processor.SinkFactory
 */
public class PaimonSinkHelper {

    private static final Logger LOG = LoggerFactory.getLogger(PaimonSinkHelper.class);

    private static final String DATABASE_NAME = "basket_analysis";
    private static final String TABLE_NAME = "basket_patterns";

    /**
     * Setup Paimon sink for basket patterns.
     *
     * @param patterns DataStream of basket patterns from PatternMinerFunction
     * @param paimonWarehouse Paimon warehouse path (e.g., /tmp/paimon)
     * @param tEnv Stream Table Environment
     */
    public static void setupPaimonSink(
            DataStream<BasketPattern> patterns,
            String paimonWarehouse,
            StreamTableEnvironment tEnv) {

        try {
            LOG.info("📦 Setting up Paimon sink");
            LOG.info("   Warehouse: {}", paimonWarehouse);
            LOG.info("   Database: {}", DATABASE_NAME);
            LOG.info("   Table: {}", TABLE_NAME);

            // Create Paimon catalog
            createPaimonCatalog(tEnv, paimonWarehouse);

            // Create database if not exists
            tEnv.executeSql(String.format(
                "CREATE DATABASE IF NOT EXISTS %s", DATABASE_NAME));

            // Create table if not exists
            createPaimonTable(tEnv);

            // Convert DataStream to Table
            Table table = tEnv.fromDataStream(
                patterns,
                Schema.newBuilder()
                    .column("antecedents", "ARRAY<STRING>")
                    .column("consequent", "STRING")
                    .column("support", "DOUBLE")
                    .column("confidence", "DOUBLE")
                    .column("lift", "DOUBLE")
                    .column("category", "STRING")
                    .column("userId", "STRING")
                    .column("sessionId", "STRING")
                    .column("timestamp", "BIGINT")
                    .columnByExpression("pattern_id",
                        "CONCAT(ARRAY_JOIN(antecedents, ','), '_', consequent)")
                    .columnByExpression("created_at",
                        "TO_TIMESTAMP(FROM_UNIXTIME(timestamp / 1000))")
                    .columnByExpression("event_time",
                        "TO_TIMESTAMP(FROM_UNIXTIME(timestamp / 1000))")
                    .build()
            );

            // Insert into Paimon table
            table.select(
                $("pattern_id"),
                $("antecedents"),
                $("consequent"),
                $("support"),
                $("confidence"),
                $("lift"),
                $("category"),
                $("userId"),
                $("sessionId"),
                $("created_at"),
                $("event_time")
            ).executeInsert(String.format("%s.%s", DATABASE_NAME, TABLE_NAME));

            LOG.info("✅ Paimon sink configured successfully");
            LOG.info("   Patterns will be written to: {}/{}.{}",
                paimonWarehouse, DATABASE_NAME, TABLE_NAME);

        } catch (Exception e) {
            LOG.error("❌ Failed to setup Paimon sink", e);
            LOG.warn("   Continuing without Paimon sink (patterns only in Kafka)");
        }
    }

    /**
     * Create Paimon catalog in Flink Table API.
     */
    private static void createPaimonCatalog(StreamTableEnvironment tEnv, String warehouse) {
        String catalogDDL = String.format(
            "CREATE CATALOG IF NOT EXISTS paimon_catalog WITH (\n" +
            "  'type' = 'paimon',\n" +
            "  'warehouse' = '%s'\n" +
            ")", warehouse);

        LOG.debug("Creating Paimon catalog: {}", catalogDDL);
        tEnv.executeSql(catalogDDL);

        // Use the Paimon catalog
        tEnv.executeSql("USE CATALOG paimon_catalog");
    }

    /**
     * Create Paimon table for basket patterns.
     *
     * Features:
     * - Primary key on pattern_id (enables updates)
     * - Auto-compaction
     * - Changelog enabled
     * - Snapshot expiration after 7 days
     */
    private static void createPaimonTable(StreamTableEnvironment tEnv) {
        String tableDDL = String.format(
            "CREATE TABLE IF NOT EXISTS %s.%s (\n" +
            "  pattern_id STRING,\n" +
            "  antecedents ARRAY<STRING>,\n" +
            "  consequent STRING,\n" +
            "  support DOUBLE,\n" +
            "  confidence DOUBLE,\n" +
            "  lift DOUBLE,\n" +
            "  category STRING,\n" +
            "  user_id STRING,\n" +
            "  session_id STRING,\n" +
            "  created_at TIMESTAMP(3),\n" +
            "  event_time TIMESTAMP(3),\n" +
            "  PRIMARY KEY (pattern_id) NOT ENFORCED\n" +
            ") WITH (\n" +
            "  'bucket' = '4',\n" +
            "  'changelog-producer' = 'input',\n" +
            "  'snapshot.time-retained' = '7d',\n" +
            "  'snapshot.num-retained.min' = '10',\n" +
            "  'snapshot.num-retained.max' = '100',\n" +
            "  'compaction.min.file-num' = '5',\n" +
            "  'compaction.max.file-num' = '50'\n" +
            ")", DATABASE_NAME, TABLE_NAME);

        LOG.debug("Creating Paimon table: {}", tableDDL);
        tEnv.executeSql(tableDDL);
    }

    /**
     * Initialize Paimon catalog and table (can be called standalone).
     *
     * @param warehouse Paimon warehouse path
     * @throws Exception if initialization fails
     */
    public static void initializePaimonTable(String warehouse) throws Exception {
        LOG.info("🔧 Initializing Paimon table structure");

        // Create catalog
        Options catalogOptions = new Options();
        catalogOptions.set("warehouse", warehouse);

        CatalogContext catalogContext = CatalogContext.create(catalogOptions);
        Catalog catalog = CatalogFactory.createCatalog(catalogContext);

        // Create database
        try {
            catalog.createDatabase(DATABASE_NAME, true);
            LOG.info("✓ Created database: {}", DATABASE_NAME);
        } catch (Exception e) {
            LOG.debug("Database {} already exists", DATABASE_NAME);
        }

        // Note: Table will be created automatically by Paimon on first write
        // or explicitly via Flink SQL DDL

        LOG.info("✅ Paimon initialization complete");
        LOG.info("   Warehouse: {}", warehouse);
        LOG.info("   Database: {}", DATABASE_NAME);
        LOG.info("   Table: {} (will be created on first write)", TABLE_NAME);
    }

    /**
     * Get the full table path for querying.
     */
    public static String getTablePath() {
        return String.format("%s.%s", DATABASE_NAME, TABLE_NAME);
    }

    /**
     * Get sample query for reading patterns from Paimon.
     */
    public static String getSampleQuery() {
        return String.format(
            "-- Query basket patterns from Paimon\n" +
            "SELECT \n" +
            "  pattern_id,\n" +
            "  ARRAY_JOIN(antecedents, ', ') as antecedents,\n" +
            "  consequent,\n" +
            "  confidence,\n" +
            "  support,\n" +
            "  lift,\n" +
            "  category,\n" +
            "  created_at\n" +
            "FROM %s\n" +
            "WHERE confidence > 0.5\n" +
            "ORDER BY confidence DESC\n" +
            "LIMIT 10;", getTablePath());
    }
}
