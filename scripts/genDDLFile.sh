#!/bin/bash

# Check if a description was provided
if [ $# -eq 0 ]; then
    echo "Usage: ./genDDLFile.sh \"your migration description\""
    exit 1
fi

# Configuration
MIGRATION_DIR="apps/api/src/main/resources/db/migration"
DESCRIPTION=$(echo "$1" | tr '[:upper:]' '[:lower:]' | tr ' ' '_' | sed 's/["\x27]//g')
TIMESTAMP=$(date +%Y%m%d%H%M%S)
FILENAME="V${TIMESTAMP}__${DESCRIPTION}.sql"

# Create migrations directory if it doesn't exist
mkdir -p "$MIGRATION_DIR"

# Create the migration file
FILEPATH="$MIGRATION_DIR/$FILENAME"
touch "$FILEPATH"

echo "-- Migration file created: $FILENAME"
echo "-- Description: $1"
echo "-- Path: $FILEPATH"

# Optional: Open the file in the default editor
# Uncomment the line for your preferred editor
# code "$FILEPATH"  # VS Code
# idea "$FILEPATH"  # IntelliJ IDEA