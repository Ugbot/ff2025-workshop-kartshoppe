#!/bin/bash
# Start the Inventory Management Flink Job

echo "🚀 Starting Inventory Management Flink Job..."

# Set environment variables
export KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-localhost:19092}
export INITIAL_PRODUCTS_FILE=${INITIAL_PRODUCTS_FILE:-data/initial-products.json}

# Build the job if needed
echo "📦 Building Inventory Flink Job..."
./gradlew :flink-inventory:shadowJar

# Run the job
echo "▶️ Running Inventory Management Job..."
java -cp flink-inventory/build/libs/flink-inventory.jar \
  com.evoura.ververica.composable_job.flink.inventory.InventoryManagementJob

echo "✅ Inventory Management Job started"