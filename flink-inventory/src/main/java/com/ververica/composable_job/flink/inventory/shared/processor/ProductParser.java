package com.ververica.composable_job.flink.inventory.shared.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ververica.composable_job.model.ecommerce.Product;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reusable ProcessFunction to parse JSON strings into Product objects.
 *
 * PATTERN: Data Transformation / Parsing
 *
 * Handles both:
 * - JSON arrays: [{"productId":"P001",...}, {"productId":"P002",...}]
 * - Single objects: {"productId":"P001",...}
 *
 * This is extracted from Pattern 01 (Hybrid Source) but made reusable
 * across the entire inventory management pipeline.
 *
 * USAGE:
 * <pre>
 * DataStream<String> rawJson = ...;
 * DataStream<Product> products = rawJson.process(new ProductParser());
 * </pre>
 *
 * ERROR HANDLING:
 * - Logs errors but doesn't fail the job
 * - Continues processing valid records
 * - Use side outputs for error handling in production
 */
public class ProductParser extends ProcessFunction<String, Product> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(ProductParser.class);

    // Transient because ObjectMapper is not serializable
    // Will be recreated on each task manager
    private transient ObjectMapper mapper;

    @Override
    public void open(org.apache.flink.configuration.Configuration parameters) {
        // Initialize ObjectMapper once per task
        mapper = new ObjectMapper();
        LOG.info("ProductParser initialized");
    }

    @Override
    public void processElement(
            String value,
            Context ctx,
            Collector<Product> out) throws Exception {

        if (value == null || value.trim().isEmpty()) {
            LOG.warn("Received null or empty input, skipping");
            return;
        }

        try {
            String trimmedValue = value.trim();

            // Handle JSON array
            if (trimmedValue.startsWith("[")) {
                Product[] products = mapper.readValue(trimmedValue, Product[].class);
                for (Product product : products) {
                    if (isValid(product)) {
                        LOG.debug("Parsed product from array: {}", product.productId);
                        out.collect(product);
                    } else {
                        LOG.warn("Invalid product in array, skipping: {}", product);
                    }
                }
            }
            // Handle single JSON object
            else if (trimmedValue.startsWith("{")) {
                Product product = mapper.readValue(trimmedValue, Product.class);
                if (isValid(product)) {
                    LOG.debug("Parsed single product: {}", product.productId);
                    out.collect(product);
                } else {
                    LOG.warn("Invalid product, skipping: {}", product);
                }
            }
            // Not JSON
            else {
                LOG.warn("Input is not JSON (doesn't start with [ or {{): {}",
                    trimmedValue.substring(0, Math.min(100, trimmedValue.length())));
            }

        } catch (Exception e) {
            // Log but don't fail - continue processing other records
            LOG.error("Failed to parse JSON: {}. Error: {}",
                value.substring(0, Math.min(200, value.length())),
                e.getMessage());
        }
    }

    /**
     * Validates that a product has required fields.
     * In production, this would be more comprehensive.
     */
    private boolean isValid(Product product) {
        if (product == null) {
            return false;
        }
        if (product.productId == null || product.productId.trim().isEmpty()) {
            LOG.warn("Product missing productId");
            return false;
        }
        if (product.name == null || product.name.trim().isEmpty()) {
            LOG.warn("Product {} missing name", product.productId);
            return false;
        }
        return true;
    }
}
