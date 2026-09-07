# Identity Service

Identity and Access Management (IAM) microservice for the Travel Management System. Handles user registration, role-based access control (RBAC), password hashing, and asynchronous event publishing via the Transactional Outbox Pattern.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 25 |
| Framework | Spring Boot 4.1.1 (Web MVC) |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Security | Spring Security Crypto (BCrypt) |
| Messaging | Apache Kafka (optional) |
| Serialisation | Jackson (Databind) |
| Validation | Jakarta Bean Validation |
| Build | Maven 3.9 |
| Containerisation | Docker / Docker Compose |

---

## Architecture Overview

```
HTTP Request
     │
     ▼
AuthController  (POST /auth/register)
     │
     ▼
AuthService
  ├── Validates email uniqueness
  ├── Hashes password (BCrypt)
  ├── Assigns default TRAVELER role
  ├── Persists User
  └── OutboxEventPublisher
          ├── Writes OutboxEvent record (same transaction)
          └── After commit → KafkaUserEventPublisher
                                └── Publishes to Kafka topic (if enabled)

OutboxEventRelayService  (scheduled, every 10 s by default)
  └── Polls unpublished outbox_events → re-publishes to Kafka → marks published
```

The service uses the **Transactional Outbox Pattern** to guarantee at-least-once event delivery. A `UserRegisteredEvent` is written to the `outbox_events` table within the same database transaction as the user record. A separate scheduler then relays any unpublished events to Kafka, providing a safety net for transient broker failures.

---

## API Reference

### Register User

```
POST /auth/register
```

Version negotiated via `X-API-Version` request header (default: `1`). The resolved version is echoed back in the `X-API-Version` response header.

**Request body**

```json
{
  "email": "jane.doe@example.com",
  "password": "Str0ng!Pass",
  "firstName": "Jane",
  "lastName": "Doe"
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `email` | string | yes | Valid email format, max 255 chars |
| `password` | string | yes | 8–100 chars, must contain uppercase, lowercase, digit, and special character |
| `firstName` | string | yes | 1–100 chars |
| `lastName` | string | no | Max 100 chars |

**Response — 201 Created**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "jane.doe@example.com",
  "firstName": "Jane",
  "lastName": "Doe",
  "status": "PENDING",
  "emailVerified": false,
  "roles": ["TRAVELER"],
  "createdAt": "2026-09-07T02:46:54Z",
  "updatedAt": "2026-09-07T02:46:54Z"
}
```

> When `identity.registration.require-email-verification` is `false`, the initial status is `ACTIVE` and `emailVerified` is `true`.

**Error responses**

| Status | Condition |
|---|---|
| `400 Bad Request` | Validation failure or malformed JSON body |
| `400 Bad Request` | Unsupported `X-API-Version` header value |
| `409 Conflict` | Email address already registered |
| `500 Internal Server Error` | System role misconfiguration or unexpected error |

All error responses share a common shape:

```json
{
  "timestamp": "2026-09-07T02:46:54Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/auth/register",
  "errors": {
    "email": "Invalid email format",
    "password": "Password must be at least 8 characters long..."
  }
}
```

The `errors` field is only present on validation failures (field-level detail).

---

## Testing the Registration Endpoint

Make sure the service is running on port `8081` before sending requests.

### curl

Minimal request (last name optional):

```bash
curl -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "jane.doe@example.com",
    "password": "Str0ng!Pass1",
    "firstName": "Jane",
    "lastName": "Doe"
  }'
```

With an explicit API version header:

```bash
curl -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -H "X-API-Version: 1" \
  -d '{
    "email": "jane.doe@example.com",
    "password": "Str0ng!Pass1",
    "firstName": "Jane",
    "lastName": "Doe"
  }'
```

Use `-i` to see response headers (including the echoed `X-API-Version`):

```bash
curl -i -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -H "X-API-Version: 1" \
  -d '{
    "email": "jane.doe@example.com",
    "password": "Str0ng!Pass1",
    "firstName": "Jane"
  }'
```

### Postman / Insomnia

1. Set method to **POST** and URL to `http://localhost:8081/auth/register`.
2. Under **Headers**, add:
   - `Content-Type: application/json`
   - `X-API-Version: 1` *(optional — defaults to `1` if omitted)*
3. Under **Body**, select **raw → JSON** and paste:

```json
{
  "email": "jane.doe@example.com",
  "password": "Str0ng!Pass1",
  "firstName": "Jane",
  "lastName": "Doe"
}
```

4. Send. A successful registration returns **201 Created** with the user object in the body.

### HTTPie

```bash
http POST http://localhost:8081/auth/register \
  Content-Type:application/json \
  X-API-Version:1 \
  email=jane.doe@example.com \
  password=Str0ng!Pass1 \
  firstName=Jane \
  lastName=Doe
```

---

## Data Model

### Users (`users`)

| Column | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key, auto-generated |
| `email` | VARCHAR(255) | Unique, normalised to lowercase |
| `password_hash` | VARCHAR(255) | BCrypt hash |
| `first_name` | VARCHAR(100) | — |
| `last_name` | VARCHAR(100) | Nullable |
| `status` | VARCHAR(20) | `ACTIVE`, `PENDING`, or `DEACTIVATED` |
| `email_verified` | BOOLEAN | — |
| `created_at` | TIMESTAMPTZ | Immutable |
| `updated_at` | TIMESTAMPTZ | Auto-updated via trigger |

### Roles (`roles`) and Permissions (`permissions`)

Three default roles seeded by `V2__seed_roles.sql`:

| Role | Description |
|---|---|
| `ADMIN` | Full access to all permissions |
| `TRAVEL_MANAGER` | Approve bookings, manage policies, view reports |
| `TRAVELER` | Submit and manage own bookings and expenses |

Permissions cover users, roles, trips, policies, expenses, and reports. Roles and permissions are linked via the `role_permissions` join table. Users receive roles via `user_roles`. New registrations are automatically assigned the `TRAVELER` role.

### Outbox Events (`outbox_events`)

| Column | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key, auto-generated |
| `event_type` | VARCHAR(255) | e.g. `UserRegisteredEvent` |
| `payload` | TEXT | JSON-serialised event |
| `created_at` | TIMESTAMPTZ | — |
| `published` | BOOLEAN | `false` until successfully relayed |

A partial index on `published = FALSE` keeps polling queries efficient.

---

## Database Migrations

Flyway runs automatically on startup. Scripts live in `src/main/resources/db/migration/`:

| Version | File | Description |
|---|---|---|
| V1 | `V1__init_schema.sql` | Creates `users`, `roles`, `permissions`, `user_roles`, `role_permissions`; indexes; `updated_at` triggers |
| V2 | `V2__seed_roles.sql` | Seeds default roles, permissions, and role-permission associations |
| V3 | `V3__add_user_names.sql` | Adds `first_name` and `last_name` columns to `users` |
| V4 | `V4__create_outbox_table.sql` | Creates `outbox_events` table and polling index |

To add a new migration, create a file following the naming convention:

```
V<next_version>__<short_description>.sql
```

---

## Configuration

### Priority Order

1. Command-line arguments (`--server.port=8081`)
2. Environment variables
3. `src/main/resources/application-secrets.properties` *(optional, git-ignored)*
4. `src/main/resources/application.properties` *(defaults)*

### Environment Variables Reference

| Environment Variable | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8081` | HTTP port |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/identity_db` | Full JDBC URL; overrides `DB_HOST`/`DB_PORT`/`DB_NAME` |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | *(required)* | Database password |
| `DB_HOST` | `localhost` | Used in the default JDBC URL |
| `DB_PORT` | `5432` | Used in the default JDBC URL (`5433` when using Docker Compose) |
| `DB_NAME` | `identity_db` | Used in the default JDBC URL |
| `JWT_SECRET` | `change-me-in-production` | Secret for signing JWTs |
| `JWT_EXPIRATION` | `86400000` (24 h) | JWT TTL in milliseconds |
| `REQUIRE_EMAIL_VERIFICATION` | `true` | `false` sets new users to `ACTIVE` immediately |
| `KAFKA_ENABLED` | `false` | Enable live Kafka publishing |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `KAFKA_TOPIC_USER_REGISTERED` | `user.registered` | Topic for `UserRegisteredEvent` |
| `OUTBOX_RELAY_FIXED_DELAY_MS` | `10000` | Delay between outbox relay runs (ms) |
| `OUTBOX_RELAY_BATCH_SIZE` | `50` | Maximum events per relay run |

### Local Development Secrets

To avoid exporting shell variables, create `src/main/resources/application-secrets.properties` (this file is git-ignored):

```properties
jwt.secret=your-secure-local-jwt-secret-key-min-256-bits
spring.datasource.password=postgres
```

---

## Running the Service

### Option 1: Docker Compose (Full Stack)

Starts both PostgreSQL and the Identity Service. Migrations run automatically.

```bash
# Start all services
docker compose up --build -d

# Follow service logs
docker compose logs -f identity-service

# Stop (preserves database volume)
docker compose down

# Stop and remove database volume (clean restart)
docker compose down -v
```

### Option 2: Docker Database + Local Maven

Run PostgreSQL via Docker Compose and the service locally via Maven:

```bash
# 1. Start only PostgreSQL (exposed on 5433)
docker compose up -d postgres

# 2. Run the service
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/identity_db \
SPRING_DATASOURCE_USERNAME=postgres \
SPRING_DATASOURCE_PASSWORD=postgres \
./mvnw spring-boot:run
```

### Option 3: Local PostgreSQL

If you have PostgreSQL running locally on port `5432`:

```bash
# 1. Create the database
createdb identity_db

# 2. Run the service (password must be set)
SPRING_DATASOURCE_PASSWORD=postgres ./mvnw spring-boot:run
```

---

## Connecting to the Database

### psql

```bash
# Docker Compose PostgreSQL (port 5433)
PGPASSWORD=postgres psql -h localhost -p 5433 -U postgres -d identity_db

# Local PostgreSQL (port 5432)
PGPASSWORD=postgres psql -h localhost -p 5432 -U postgres -d identity_db
```

### GUI Clients (DBeaver, DataGrip, pgAdmin)

| Parameter | Docker Compose | Local PostgreSQL |
|---|---|---|
| Host | `localhost` | `localhost` |
| Port | `5433` | `5432` |
| Database | `identity_db` | `identity_db` |
| Username | `postgres` | `postgres` |
| Password | `postgres` | `postgres` |

---

## Testing

Integration tests use [Testcontainers](https://testcontainers.com/) to spin up an isolated PostgreSQL instance automatically — no local database required.

```bash
./mvnw clean test
```

Test coverage includes:

- `AuthRegistrationIntegrationTest` — end-to-end registration flow via HTTP
- `AuthServiceTest` — unit tests for `AuthService` business logic
- `KafkaUserEventPublisherTest` — Kafka publisher behaviour (enabled/disabled)
- `RegisterRequestValidationTest` — Bean Validation constraints on `RegisterRequest`
- `DatabaseMigrationTests` — verifies all Flyway migrations apply cleanly

---

## CI/CD

GitHub Actions workflows in `.github/workflows/`:

| Workflow | Trigger | Description |
|---|---|---|
| `build.yml` | Push / PR to `main` | Compiles, runs tests (`mvn verify`), and optionally runs SonarQube analysis |
| `pr-validator.yml` | Pull request | Validates PR title and description against the PR template |
| `ping.yml` | Manual / scheduled | Connectivity health check |

SonarQube analysis runs only when `SONAR_TOKEN` and `SONAR_HOST_URL` secrets are configured in the repository.
