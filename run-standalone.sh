#!/bin/bash
# Run BuddiLive standalone with embedded Jetty + Derby on port 8686.

set -e
cd "$(dirname "$0")"

if [ -f .env ]; then
    set -a
    source .env
    set +a
fi

mvn package exec:java -Pstandalone -DskipTests
