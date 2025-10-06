package com.ververica.composable_job.flink.recommendations;

import com.ververica.composable_job.flink.recommendations.ml.BasketPattern;
import com.ververica.composable_job.flink.recommendations.shared.processor.PaimonSinkHelper;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Generates historical basket patterns for ML training.
 *
 * USAGE:
 * This standalone job populates the Paimon table with realistic basket patterns
 * to enable ML model training before real patterns accumulate from live traffic.
 *
 * PATTERN CATEGORIES:
 * - Electronics: laptop → mouse, keyboard, monitor, headset
 * - Home & Kitchen: coffee_maker → coffee_beans, filters
 * - Fashion: shirt → pants, shoes, belt
 * - Sports: yoga_mat → yoga_blocks, water_bottle
 *
 * METRICS:
 * - Confidence: 0.3 - 0.95 (realistic association strength)
 * - Support: 0.01 - 0.3 (realistic frequency)
 * - Lift: 1.1 - 3.0 (positive correlations only)
 *
 * TEMPORAL DISTRIBUTION:
 * - Patterns distributed over past 90 days
 * - More recent patterns have higher weights
 * - Simulates realistic historical accumulation
 */
public class HistoricalBasketDataGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(HistoricalBasketDataGenerator.class);

    private static final Random RANDOM = new Random(42); // Fixed seed for reproducibility

    // Configuration
    private static final int NUM_PATTERNS = 5000; // Number of patterns to generate
    private static final int DAYS_HISTORY = 90;   // Distribute patterns over 90 days

    public static void main(String[] args) throws Exception {
        LOG.info("🏗️  Historical Basket Data Generator");
        LOG.info("   Generating {} patterns over {} days", NUM_PATTERNS, DAYS_HISTORY);

        // Get Paimon warehouse path from environment or use default
        String paimonWarehouse = System.getenv().getOrDefault(
            "PAIMON_WAREHOUSE",
            "/tmp/paimon"
        );

        LOG.info("   Paimon warehouse: {}", paimonWarehouse);

        // Create Flink environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1); // Single parallelism for deterministic generation

        StreamTableEnvironment tEnv = StreamTableEnvironment.create(env);

        // Generate patterns
        List<BasketPattern> patterns = generatePatterns();
        LOG.info("✓ Generated {} patterns", patterns.size());

        // Create DataStream from collection
        DataStream<BasketPattern> patternStream = env
            .fromCollection(patterns)
            .assignTimestampsAndWatermarks(
                WatermarkStrategy
                    .<BasketPattern>forMonotonousTimestamps()
                    .withTimestampAssigner((pattern, timestamp) -> pattern.getTimestamp())
            );

        // Setup Paimon sink
        LOG.info("📦 Setting up Paimon sink...");
        PaimonSinkHelper.setupPaimonSink(patternStream, paimonWarehouse, tEnv);

        // Execute
        LOG.info("🚀 Starting data generation job...");
        env.execute("Historical Basket Data Generator");

        LOG.info("✅ Historical data generation complete!");
        LOG.info("   {} patterns written to Paimon", NUM_PATTERNS);
        LOG.info("   Query with: {}", PaimonSinkHelper.getSampleQuery());
    }

    /**
     * Generate realistic basket patterns across multiple categories.
     */
    private static List<BasketPattern> generatePatterns() {
        List<BasketPattern> patterns = new ArrayList<>();

        // Define pattern templates by category
        Map<String, List<PatternTemplate>> categoryTemplates = createPatternTemplates();

        // Calculate timestamp range
        long nowMillis = System.currentTimeMillis();
        long startMillis = Instant.now().minus(DAYS_HISTORY, ChronoUnit.DAYS).toEpochMilli();

        // Generate patterns
        for (int i = 0; i < NUM_PATTERNS; i++) {
            // Select random category
            String category = selectRandomCategory();
            List<PatternTemplate> templates = categoryTemplates.get(category);

            // Select random template
            PatternTemplate template = templates.get(RANDOM.nextInt(templates.size()));

            // Generate timestamp (more recent patterns are more common)
            long timestamp = generateTimestamp(startMillis, nowMillis);

            // Create pattern with some variability in metrics
            BasketPattern pattern = new BasketPattern(
                template.antecedents,
                template.consequent,
                template.support + (RANDOM.nextDouble() - 0.5) * 0.05, // ±5% variability
                template.confidence + (RANDOM.nextDouble() - 0.5) * 0.1, // ±10% variability
                template.lift + (RANDOM.nextDouble() - 0.5) * 0.2, // ±0.2 variability
                category,
                generateUserId(),
                generateSessionId(),
                timestamp
            );

            patterns.add(pattern);
        }

        // Sort by timestamp for better insertion performance
        patterns.sort(Comparator.comparingLong(BasketPattern::getTimestamp));

        return patterns;
    }

    /**
     * Create pattern templates for each category.
     */
    private static Map<String, List<PatternTemplate>> createPatternTemplates() {
        Map<String, List<PatternTemplate>> templates = new HashMap<>();

        // Electronics patterns
        List<PatternTemplate> electronics = Arrays.asList(
            new PatternTemplate(Arrays.asList("prod_laptop_001"), "prod_mouse_001", 0.15, 0.75, 2.1, "electronics"),
            new PatternTemplate(Arrays.asList("prod_laptop_001"), "prod_keyboard_001", 0.12, 0.68, 1.9, "electronics"),
            new PatternTemplate(Arrays.asList("prod_laptop_001"), "prod_monitor_001", 0.08, 0.45, 1.6, "electronics"),
            new PatternTemplate(Arrays.asList("prod_laptop_001"), "prod_headset_001", 0.10, 0.52, 1.8, "electronics"),
            new PatternTemplate(Arrays.asList("prod_mouse_001", "prod_keyboard_001"), "prod_laptop_001", 0.09, 0.82, 2.5, "electronics"),
            new PatternTemplate(Arrays.asList("prod_phone_001"), "prod_charger_001", 0.20, 0.85, 2.8, "electronics"),
            new PatternTemplate(Arrays.asList("prod_phone_001"), "prod_case_001", 0.18, 0.78, 2.4, "electronics"),
            new PatternTemplate(Arrays.asList("prod_camera_001"), "prod_sd_card_001", 0.14, 0.88, 2.9, "electronics"),
            new PatternTemplate(Arrays.asList("prod_camera_001"), "prod_tripod_001", 0.11, 0.62, 2.0, "electronics"),
            new PatternTemplate(Arrays.asList("prod_tablet_001"), "prod_stylus_001", 0.13, 0.71, 2.2, "electronics")
        );
        templates.put("electronics", electronics);

        // Home & Kitchen patterns
        List<PatternTemplate> homeKitchen = Arrays.asList(
            new PatternTemplate(Arrays.asList("prod_coffee_maker_001"), "prod_coffee_beans_001", 0.22, 0.89, 2.7, "home_kitchen"),
            new PatternTemplate(Arrays.asList("prod_coffee_maker_001"), "prod_filters_001", 0.19, 0.81, 2.5, "home_kitchen"),
            new PatternTemplate(Arrays.asList("prod_blender_001"), "prod_recipe_book_001", 0.07, 0.42, 1.5, "home_kitchen"),
            new PatternTemplate(Arrays.asList("prod_pan_001"), "prod_spatula_001", 0.16, 0.74, 2.3, "home_kitchen"),
            new PatternTemplate(Arrays.asList("prod_toaster_001"), "prod_bread_knife_001", 0.09, 0.48, 1.7, "home_kitchen"),
            new PatternTemplate(Arrays.asList("prod_mixer_001"), "prod_mixing_bowl_001", 0.12, 0.68, 2.1, "home_kitchen"),
            new PatternTemplate(Arrays.asList("prod_pot_set_001"), "prod_lid_organizer_001", 0.08, 0.51, 1.8, "home_kitchen"),
            new PatternTemplate(Arrays.asList("prod_knife_set_001"), "prod_cutting_board_001", 0.17, 0.79, 2.4, "home_kitchen")
        );
        templates.put("home_kitchen", homeKitchen);

        // Fashion patterns
        List<PatternTemplate> fashion = Arrays.asList(
            new PatternTemplate(Arrays.asList("prod_shirt_001"), "prod_pants_001", 0.25, 0.72, 2.0, "fashion"),
            new PatternTemplate(Arrays.asList("prod_shirt_001"), "prod_belt_001", 0.11, 0.58, 1.9, "fashion"),
            new PatternTemplate(Arrays.asList("prod_dress_001"), "prod_shoes_001", 0.18, 0.76, 2.3, "fashion"),
            new PatternTemplate(Arrays.asList("prod_jacket_001"), "prod_scarf_001", 0.09, 0.47, 1.6, "fashion"),
            new PatternTemplate(Arrays.asList("prod_jeans_001"), "prod_tshirt_001", 0.21, 0.69, 2.1, "fashion"),
            new PatternTemplate(Arrays.asList("prod_suit_001"), "prod_tie_001", 0.16, 0.83, 2.6, "fashion"),
            new PatternTemplate(Arrays.asList("prod_sneakers_001"), "prod_socks_001", 0.19, 0.71, 2.2, "fashion"),
            new PatternTemplate(Arrays.asList("prod_hat_001"), "prod_sunglasses_001", 0.10, 0.54, 1.8, "fashion")
        );
        templates.put("fashion", fashion);

        // Sports & Outdoors patterns
        List<PatternTemplate> sports = Arrays.asList(
            new PatternTemplate(Arrays.asList("prod_yoga_mat_001"), "prod_yoga_blocks_001", 0.14, 0.67, 2.0, "sports"),
            new PatternTemplate(Arrays.asList("prod_yoga_mat_001"), "prod_water_bottle_001", 0.17, 0.72, 2.2, "sports"),
            new PatternTemplate(Arrays.asList("prod_running_shoes_001"), "prod_running_socks_001", 0.20, 0.78, 2.4, "sports"),
            new PatternTemplate(Arrays.asList("prod_bicycle_001"), "prod_helmet_001", 0.23, 0.87, 2.8, "sports"),
            new PatternTemplate(Arrays.asList("prod_bicycle_001"), "prod_bike_lock_001", 0.19, 0.81, 2.5, "sports"),
            new PatternTemplate(Arrays.asList("prod_tent_001"), "prod_sleeping_bag_001", 0.15, 0.73, 2.3, "sports"),
            new PatternTemplate(Arrays.asList("prod_dumbbells_001"), "prod_workout_mat_001", 0.13, 0.64, 2.0, "sports"),
            new PatternTemplate(Arrays.asList("prod_tennis_racket_001"), "prod_tennis_balls_001", 0.22, 0.89, 2.9, "sports")
        );
        templates.put("sports", sports);

        return templates;
    }

    /**
     * Select random category with weighted distribution.
     */
    private static String selectRandomCategory() {
        // Weight: electronics (40%), fashion (25%), home_kitchen (20%), sports (15%)
        double rand = RANDOM.nextDouble();
        if (rand < 0.40) return "electronics";
        if (rand < 0.65) return "fashion";
        if (rand < 0.85) return "home_kitchen";
        return "sports";
    }

    /**
     * Generate timestamp with bias towards more recent dates.
     */
    private static long generateTimestamp(long startMillis, long nowMillis) {
        // Use exponential distribution to favor recent timestamps
        double lambda = 2.0;
        double uniform = RANDOM.nextDouble();
        double exponential = -Math.log(1 - uniform) / lambda;

        // Clamp to [0, 1]
        exponential = Math.min(1.0, exponential);

        // Map to timestamp range
        long range = nowMillis - startMillis;
        return startMillis + (long) (exponential * range);
    }

    /**
     * Generate random user ID.
     */
    private static String generateUserId() {
        return "hist_user_" + (RANDOM.nextInt(1000) + 1);
    }

    /**
     * Generate random session ID.
     */
    private static String generateSessionId() {
        return "hist_session_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Pattern template for generating variations.
     */
    private static class PatternTemplate {
        final List<String> antecedents;
        final String consequent;
        final double support;
        final double confidence;
        final double lift;
        final String category;

        PatternTemplate(List<String> antecedents, String consequent,
                       double support, double confidence, double lift, String category) {
            this.antecedents = antecedents;
            this.consequent = consequent;
            this.support = support;
            this.confidence = confidence;
            this.lift = lift;
            this.category = category;
        }
    }
}
