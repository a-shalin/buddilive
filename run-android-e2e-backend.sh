#!/bin/bash
# Run BuddiLive backend for Android E2E with a fresh Derby DB on port 8081.

set -e
cd "$(dirname "$0")"

if [ -f .env ]; then
    set -a
    source .env
    set +a
fi

mvn clean package -Pe2etest -DskipTests
exec java -jar target/buddilive.jar --spring.profiles.active=androide2e
