#!/bin/bash

echo "🏗️  Initializing Paimon Table & Historical Training Data"
echo "=========================================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
PAIMON_WAREHOUSE="${PAIMON_WAREHOUSE:-/tmp/paimon}"
NUM_PATTERNS="${NUM_PATTERNS:-5000}"

echo -e "\n${BLUE}Configuration:${NC}"
echo "  Paimon Warehouse: $PAIMON_WAREHOUSE"
echo "  Number of Patterns: $NUM_PATTERNS"

# Step 1: Create Paimon directory if needed
echo -e "\n${BLUE}Step 1: Preparing Paimon warehouse${NC}"
if [ ! -d "$PAIMON_WAREHOUSE" ]; then
    echo "Creating warehouse directory: $PAIMON_WAREHOUSE"
    mkdir -p "$PAIMON_WAREHOUSE"
    echo -e "${GREEN}✓${NC} Directory created"
else
    echo -e "${GREEN}✓${NC} Directory exists"
fi

# Step 2: Build the Flink job
echo -e "\n${BLUE}Step 2: Building Flink recommendations module${NC}"
./gradlew :flink-recommendations:build -x buildDeepNetts -x test

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓${NC} Build successful"
else
    echo -e "${RED}✗${NC} Build failed"
    echo "Try running: ./gradlew :flink-recommendations:compileJava -x buildDeepNetts"
    exit 1
fi

# Step 3: Run the historical data generator
echo -e "\n${BLUE}Step 3: Generating historical basket patterns${NC}"
echo "This will create $NUM_PATTERNS patterns distributed over 90 days"
echo -e "${YELLOW}This may take 30-60 seconds...${NC}"
echo ""

export PAIMON_WAREHOUSE
export NUM_PATTERNS

# Run the generator using gradle
./gradlew :flink-recommendations:run -PmainClass=com.ververica.composable_job.flink.recommendations.HistoricalBasketDataGenerator

if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}✅ Historical data generation complete!${NC}"
else
    echo -e "${RED}✗${NC} Data generation failed"
    exit 1
fi

# Step 4: Verify Paimon table
echo -e "\n${BLUE}Step 4: Verifying Paimon table${NC}"
if [ -d "$PAIMON_WAREHOUSE/basket_analysis.db/basket_patterns" ]; then
    echo -e "${GREEN}✓${NC} Paimon table created successfully"
    echo ""
    echo "Table location:"
    echo "  $PAIMON_WAREHOUSE/basket_analysis.db/basket_patterns"
    echo ""

    # Show directory size
    TABLE_SIZE=$(du -sh "$PAIMON_WAREHOUSE/basket_analysis.db/basket_patterns" | cut -f1)
    echo "Table size: $TABLE_SIZE"

    # Count files
    FILE_COUNT=$(find "$PAIMON_WAREHOUSE/basket_analysis.db/basket_patterns" -type f | wc -l | tr -d ' ')
    echo "Files created: $FILE_COUNT"
else
    echo -e "${YELLOW}⚠${NC} Table directory not found at expected location"
    echo "Checking warehouse contents:"
    ls -la "$PAIMON_WAREHOUSE" 2>/dev/null || echo "Warehouse is empty"
fi

# Step 5: Summary & Next Steps
echo -e "\n${BLUE}========================================${NC}"
echo -e "${GREEN}✅ Paimon Initialization Complete!${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${BLUE}What was created:${NC}"
echo "  • Paimon catalog at $PAIMON_WAREHOUSE"
echo "  • Database: basket_analysis"
echo "  • Table: basket_patterns"
echo "  • $NUM_PATTERNS historical patterns (90-day distribution)"
echo ""
echo -e "${BLUE}Pattern Categories:${NC}"
echo "  • Electronics (40%): laptop→mouse, phone→charger, etc."
echo "  • Fashion (25%): shirt→pants, dress→shoes, etc."
echo "  • Home & Kitchen (20%): coffee_maker→beans, pan→spatula, etc."
echo "  • Sports (15%): yoga_mat→blocks, bike→helmet, etc."
echo ""
echo -e "${BLUE}Metrics Generated:${NC}"
echo "  • Confidence: 0.3 - 0.95 (association strength)"
echo "  • Support: 0.01 - 0.3 (frequency)"
echo "  • Lift: 1.1 - 3.0 (positive correlations)"
echo ""
echo -e "${BLUE}Next Steps:${NC}"
echo ""
echo "1. Query the data (using Flink SQL Client):"
echo -e "   ${YELLOW}./bin/sql-client.sh${NC}"
echo "   Then run:"
echo "   CREATE CATALOG paimon_catalog WITH ("
echo "     'type' = 'paimon',"
echo "     'warehouse' = '$PAIMON_WAREHOUSE'"
echo "   );"
echo "   USE CATALOG paimon_catalog;"
echo "   SELECT * FROM basket_analysis.basket_patterns LIMIT 10;"
echo ""
echo "2. Start the live basket analysis job (with Paimon enabled):"
echo -e "   ${YELLOW}export PAIMON_WAREHOUSE=$PAIMON_WAREHOUSE${NC}"
echo -e "   ${YELLOW}./start-basket-job.sh${NC}"
echo ""
echo "3. New patterns from live traffic will be added to this table"
echo ""
echo "4. Use this data for ML training:"
echo "   • Read patterns from Paimon"
echo "   • Train recommendation models"
echo "   • Compare with real-time pattern mining"
echo ""
echo -e "${BLUE}Sample Query:${NC}"
echo "SELECT "
echo "  category,"
echo "  COUNT(*) as pattern_count,"
echo "  AVG(confidence) as avg_confidence,"
echo "  AVG(support) as avg_support"
echo "FROM basket_analysis.basket_patterns"
echo "GROUP BY category"
echo "ORDER BY pattern_count DESC;"
echo ""
