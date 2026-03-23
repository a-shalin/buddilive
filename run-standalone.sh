#!/bin/bash
# Run BuddiLive standalone with embedded Tomcat + Derby on port 8080.

set -e
cd "$(dirname "$0")"

if [ -f .env ]; then
    set -a
    source .env
    set +a
fi

mvn clean package -Pstandalone -DskipTests
java -jar target/buddilive.jar --spring.profiles.active=standalone
