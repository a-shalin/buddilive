# Buddi Live

A web-based personal finance application for budgeting, account tracking, and financial reporting.

## Prerequisites

- Java 17+
- Maven 3.8+
- PostgreSQL (for server deployment) or Derby (embedded, for standalone)

## Build

```bash
mvn package -DskipTests
```

This produces `target/buddilive.jar` (executable Spring Boot JAR).

Build profiles control the database and mail configuration:

| Profile | Database | Use case |
|---------|----------|----------|
| `server` (default) | PostgreSQL (env vars) | Docker / production |
| `standalone` | Embedded Derby | Local use (no email needed) |
| `e2etest` | Embedded Derby | Automated tests |
| `androide2e` (runtime profile) | Embedded Derby (`target/androide2e-derby`) | Android instrumentation E2E |

Select a profile with `-P<profile>`, e.g. `mvn package -Pstandalone -DskipTests`.

## Run

### Standalone (embedded Tomcat + Derby)

**Option 1** -- from source with Maven:

```bash
./run-standalone.sh
```

This builds and starts the server on `http://localhost:8080`. Registration is direct (no email activation required).

**Option 2** -- from the JAR file directly:

```bash
mvn package -Pstandalone -DskipTests
java -jar target/buddilive.jar --spring.profiles.active=standalone
```

### Android E2E backend (fresh DB every start)

```bash
./run-android-e2e-backend.sh
```

This runs with Spring profile `androide2e` and always starts from a clean Derby DB.

Run backend + Android instrumentation E2E in one command:

```bash
./run-android-e2e-tests.sh
```

This script starts backend, waits for `http://localhost:8080`, runs Android E2E, and stops backend automatically.

### Server (PostgreSQL)

```bash
java -jar target/buddilive.jar --spring.profiles.active=server
```

Set the following environment variables:

| Variable | Description |
|----------|-------------|
| `DB_HOST` | PostgreSQL hostname |
| `DB_NAME` | Database name |
| `DB_USER` | Database user |
| `DB_PASSWORD` | Database password |
| `EMAIL` | SMTP sender address |
| `MAIL_HOST` | SMTP server |
| `MAIL_PORT` | SMTP port |
| `MAIL_AUTH` | `true` / `false` |
| `MAIL_PASSWORD` | SMTP password |
| `MAIL_TLS` | `true` / `false` |

### Docker

```bash
mvn package -DskipTests
cp target/buddilive.jar docker/
cd docker
docker build -t buddilive .
```

The Dockerfile runs the JAR with embedded Tomcat on port 8080. The production compose stack now expects a shared Traefik edge on the Docker network `edge`; `docker/docker-compose.yml.j2` contains the app + PostgreSQL services and Traefik labels.

## Test

Run all tests (unit + E2E integration):

```bash
mvn verify -Pe2etest
```

The E2E tests start an embedded Spring Boot server with Derby, drive a headless Chrome browser, and verify UI and API behavior. See [doc/E2E.md](doc/E2E.md) for details.

Run a single test:

```bash
mvn verify -Pe2etest -Dit.test=TransactionSmokeIT#testCreateTransactionAppearsInGrid
```

## Project Structure

```
src/main/java/          Java backend (Spring MVC controllers, MyBatis mappers, security)
src/main/resources/
  static/               Web frontend
    buddilive/           ExtJS application (views, controllers, stores, models)
    lib/extjs/           ExtJS 6.2 framework (debug builds)
    css/                 Stylesheets
  templates/             FreeMarker templates (index.ftlh)
  ca/.../moss/           Authentication UI (login panel, controllers)
  ca/.../buddi/          MyBatis SQL mappers
  db/changelog/          Liquibase migrations
  application.properties Spring Boot config (+ per-profile overrides)
doc/                     Documentation
src/test/                E2E and integration tests
```

See [doc/ARCHITECTURE.md](doc/ARCHITECTURE.md) for detailed architecture documentation.
