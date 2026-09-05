# Identity Service

Identity and Access Management (IAM) microservice for the Travel Management System.

---

## Configuration Overview

The Identity Service is configured via Spring Boot properties with environment variable overrides and an optional local secrets file. Configuration values are resolved in the following priority order:

1. **Command-line arguments** (e.g., `--server.port=8082`)
2. **Environment variables** (e.g., `SPRING_DATASOURCE_URL=...`)
3. **Local development secrets**: `src/main/resources/application-secrets.properties` (git-ignored, optional)
4. **Default configuration**: `src/main/resources/application.properties`

---

## Environment Variables Reference

| Environment Variable | Property Key | Default Value | Description |
|----------------------|--------------|---------------|-------------|
| `SERVER_PORT` | `server.port` | `8081` | HTTP port for the Identity Service |
| `SPRING_DATASOURCE_URL` | `spring.datasource.url` | `jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:identity_db}` | JDBC connection URL for PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | `spring.datasource.username` | `postgres` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | `spring.datasource.password` | `postgres` | Database password |
| `DB_HOST` | *(URL interpolation)* | `localhost` | Database host (fallback if `SPRING_DATASOURCE_URL` is omitted) |
| `DB_PORT` | *(URL / Compose port)* | `5433` *(Compose)* / `5432` *(Default)* | Database port |
| `DB_NAME` | *(URL interpolation)* | `identity_db` | Database catalog name |
| `JWT_SECRET` | `jwt.secret` | `change-me-in-production` | Secret key used for signing and verifying JWT tokens |
| `JWT_EXPIRATION` | `jwt.expiration` | `86400000` (24h) | JWT expiration time in milliseconds |

---

## Database Configuration

### 1. Datasource & Connection Pooling

The service uses PostgreSQL with HikariCP connection pooling configured in `application.properties`:

```properties
spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:identity_db}}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:${DB_USER:postgres}}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:${DB_PASSWORD:postgres}}
spring.datasource.driver-class-name=org.postgresql.Driver
```

Optional HikariCP performance overrides (add to environment or properties):

```properties
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=2
spring.datasource.hikari.idle-timeout=30000
spring.datasource.hikari.connection-timeout=20000
```

### 2. JPA & Hibernate Configuration

Hibernate is configured to **validate** the schema against Flyway migrations without attempting automatic DDL alterations:

```properties
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
```

---

## Flyway Migration Configuration

Flyway manages all database migrations and executes automatically on application startup.

### 1. Flyway Properties

```properties
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
```

- `spring.flyway.enabled`: Set to `false` to disable automatic migration on startup (e.g., in read-only replicas).
- `spring.flyway.locations`: Path to SQL migration scripts (default: `classpath:db/migration`).
- `spring.flyway.baseline-on-migrate`: When set to `true`, baselines existing un-migrated databases on first run.

### 2. Migration Scripts

Migrations are located in `src/main/resources/db/migration/`:

- `V1__init_schema.sql`: Creates initial schema (`users`, `roles`, `permissions`, `user_roles`, `role_permissions`), constraints, indexes, and timestamp triggers.
- `V2__seed_roles.sql`: Seeds default roles (`ADMIN`, `TRAVEL_MANAGER`, `TRAVELER`), system permissions, and role-permission associations.
- `V3__add_user_names.sql`: Adds `first_name` and `last_name` columns to the `users` table.

### 3. Adding New Migrations

Create new SQL files in `src/main/resources/db/migration/` following the Flyway versioning pattern:

```
V<Version>__<Description>.sql
```

*Example:* `V4__add_user_profile_fields.sql`

---

## JWT Configuration

JWT settings are configured in `application.properties`:

```properties
jwt.secret=${JWT_SECRET:change-me-in-production}
jwt.expiration=${JWT_EXPIRATION:86400000}
```

### Managing Local Secrets

For local development without exporting shell variables, add secrets to `src/main/resources/application-secrets.properties`:

```properties
jwt.secret=your-secure-local-jwt-secret-key-min-256-bits
jwt.expiration=86400000
```

> **Note:** `application-secrets.properties` is imported optionally via `spring.config.import=optional:classpath:application-secrets.properties` and should never be committed with production credentials.

---

## Running the Service

### Option 1: Docker Compose (Full Stack)

Builds the Identity Service and spins up a PostgreSQL container with migrations applied automatically:

```bash
# Start both database and service in background
docker compose up --build -d

# View service logs
docker compose logs -f identity-service

# Stop all containers (preserves database volume)
docker compose down

# Stop and wipe database volume (for fresh restart)
docker compose down -v
```

### Option 2: Docker Database + Local Maven Service

Run PostgreSQL via Docker Compose, and run the Identity Service locally with Maven:

```bash
# 1. Start only PostgreSQL
docker compose up -d postgres

# 2. Run the service locally (pointing to port 5433)
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/identity_db \
SPRING_DATASOURCE_USERNAME=postgres \
SPRING_DATASOURCE_PASSWORD=postgres \
./mvnw spring-boot:run
```

### Option 3: Local PostgreSQL Service

If you have a local PostgreSQL instance running on default port `5432`:

```bash
# 1. Create the database
createdb identity_db

# 2. Run the service
./mvnw spring-boot:run
```

---

## Connecting to the Database

### CLI (`psql`)

When connecting to the Docker Compose PostgreSQL container:

```bash
PGPASSWORD=postgres psql -h localhost -p 5433 -U postgres -d identity_db
```

### GUI Database Clients (DBeaver, DataGrip, pgAdmin)

| Parameter | Value |
|-----------|-------|
| **Host** | `localhost` |
| **Port** | `5433` (Docker Compose) or `5432` (Local Postgres) |
| **Database** | `identity_db` |
| **Username** | `postgres` |
| **Password** | `postgres` |

---

## Testing

Run unit and integration tests using Maven. Integration tests utilize Testcontainers and automatically manage isolated PostgreSQL test instances:

```bash
./mvnw clean test
```
