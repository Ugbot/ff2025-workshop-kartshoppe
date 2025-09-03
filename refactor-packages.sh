#!/bin/bash

# Script to refactor package structure from com.evoura.ververica to com.ververica

echo "Starting package refactoring from com.evoura.ververica to com.ververica..."

# Find all Java files
JAVA_FILES=$(find . -type f -name "*.java" -path "*/com/evoura/ververica/*")

# Update package declarations and imports in Java files
echo "Updating package declarations and imports in Java files..."
for file in $JAVA_FILES; do
    echo "Processing: $file"
    # Update package declarations
    sed -i.bak 's/package com\.evoura\.ververica/package com.ververica/g' "$file"
    # Update import statements
    sed -i.bak 's/import com\.evoura\.ververica/import com.ververica/g' "$file"
    # Update static imports
    sed -i.bak 's/import static com\.evoura\.ververica/import static com.ververica/g' "$file"
    # Remove backup files
    rm -f "${file}.bak"
done

# Update build.gradle files
echo "Updating build.gradle files..."
find . -name "build.gradle" -type f | while read -r file; do
    echo "Processing: $file"
    sed -i.bak 's/com\.evoura\.ververica/com.ververica/g' "$file"
    rm -f "${file}.bak"
done

# Update settings.gradle if it exists
if [ -f "settings.gradle" ]; then
    echo "Updating settings.gradle..."
    sed -i.bak 's/com\.evoura\.ververica/com.ververica/g' "settings.gradle"
    rm -f "settings.gradle.bak"
fi

# Update application.properties and application.yml files
echo "Updating application properties files..."
find . -name "application.properties" -o -name "application.yml" -o -name "application.yaml" | while read -r file; do
    echo "Processing: $file"
    sed -i.bak 's/com\.evoura\.ververica/com.ververica/g' "$file"
    rm -f "${file}.bak"
done

# Move files to new directory structure
echo "Moving files to new directory structure..."
for module in models quarkus-api flink-common flink-datagen flink-chat-pipeline flink-inventory; do
    if [ -d "$module/src/main/java/com/evoura/ververica" ]; then
        echo "Moving main sources in $module..."
        mkdir -p "$module/src/main/java/com/ververica"
        mv "$module/src/main/java/com/evoura/ververica/composable_job" "$module/src/main/java/com/ververica/"
        # Remove empty evoura directory
        rmdir "$module/src/main/java/com/evoura/ververica" 2>/dev/null
        rmdir "$module/src/main/java/com/evoura" 2>/dev/null
    fi
    
    if [ -d "$module/src/test/java/com/evoura/ververica" ]; then
        echo "Moving test sources in $module..."
        mkdir -p "$module/src/test/java/com/ververica"
        mv "$module/src/test/java/com/evoura/ververica/composable_job" "$module/src/test/java/com/ververica/"
        # Remove empty evoura directory
        rmdir "$module/src/test/java/com/evoura/ververica" 2>/dev/null
        rmdir "$module/src/test/java/com/evoura" 2>/dev/null
    fi
done

echo "Package refactoring complete!"
echo "Verifying new structure..."
echo "Java files in new package structure:"
find . -type f -name "*.java" -path "*/com/ververica/*" | wc -l