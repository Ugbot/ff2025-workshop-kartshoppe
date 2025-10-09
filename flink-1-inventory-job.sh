#!/bin/bash
# Start the Inventory Management Flink Job

echo "🚀 Starting Inventory Management Flink Job..."

# Get the script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Set environment variables with absolute paths
export KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-localhost:19092}
export INITIAL_PRODUCTS_FILE=${INITIAL_PRODUCTS_FILE:-$SCRIPT_DIR/data/initial-products.json}

echo "📂 Using products file: $INITIAL_PRODUCTS_FILE"

# Build the job if needed
echo "📦 Building Inventory Flink Job..."
./gradlew :flink-inventory:shadowJar

# Run the job
echo "▶️ Running Inventory Management Job..."
java --add-opens java.base/java.util=ALL-UNNAMED \
  -cp flink-inventory/build/libs/flink-inventory.jar \
  com.ververica.composable_job.flink.inventory.InventoryManagementJob

echo "✅ Inventory Management Job started"