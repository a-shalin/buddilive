#!/bin/bash
# Run BuddiLive standalone with embedded Jetty + Derby on port 8686.
# Usage: ./run-standalone.sh [compile]
#   Pass "compile" to rebuild before running.

set -e
cd "$(dirname "$0")"

if [ "$1" = "compile" ]; then
    mvn clean compile -Pstandalone
fi

mvn exec:java -Pstandalone
