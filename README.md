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

This produces `target/buddilive.war`.

Build profiles control the database and mail configuration:

| Profile | Config | Database | Use case |
|---------|--------|----------|----------|
| `server` (default) | `conf/server` | PostgreSQL (env vars) | Docker / production |
| `standalone` | `conf/standalone` | Embedded Derby | Local use |
| `e2etest` | `conf/e2etest` | Embedded Derby | Automated tests |

Select a profile with `-P<profile>`, e.g. `mvn package -Pstandalone -DskipTests`.

## Run

### Standalone (embedded Jetty + Derby)

**Option 1** -- from source with Maven:

```bash
./run-standalone.sh
```

This builds and starts the server on `http://localhost:8686/buddilive`.

Create a `.env` file in the project root to configure mail (needed for registration):

```
EMAIL=you@example.com
MAIL_PASSWORD=app-password
```

**Option 2** -- from the WAR file directly:

```bash
mvn package -Pstandalone -DskipTests
java -jar target/buddilive.war
```

The server starts on `http://localhost:8686/buddilive`. The WAR is self-contained with embedded Jetty and Derby.

### Server (PostgreSQL)

Deploy `target/buddilive.war` to any Servlet 5.0 container (Jetty 11, Tomcat 10).
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
cp target/buddilive.war docker/
cd docker
docker build -t buddilive .
```

The Dockerfile deploys the WAR to Jetty 11 on port 8080. See `docker/docker-compose.yml.j2` for a full stack with Nginx and PostgreSQL.

## Test

Run all tests (unit + E2E integration):

```bash
mvn verify -Pe2etest
```

The E2E tests start an embedded Jetty server with Derby, drive a headless Chrome browser, and verify UI and API behavior. See [doc/E2E.md](doc/E2E.md) for details.

Run a single test:

```bash
mvn verify -Pe2etest -Dit.test=TransactionSmokeIT#testCreateTransactionAppearsInGrid
```

## Project Structure

```
src/main/java/          Java backend (Restlet resources, MyBatis mappers, security)
src/main/webapp/        Web frontend
  buddilive/            ExtJS application (views, controllers, stores, models)
  lib/extjs/            ExtJS 6.2 framework (debug builds)
  css/                  Stylesheets
  index.html            FreeMarker template (entry point)
src/main/resources/     Classpath resources
  ca/.../moss/          Authentication UI (login panel, controllers)
  ca/.../buddi/         MyBatis SQL mappers
conf/                   Per-profile config.properties
doc/                    Documentation
src/test/               E2E and integration tests
```

See [doc/ARCHITECTURE.md](doc/ARCHITECTURE.md) for detailed architecture documentation.
