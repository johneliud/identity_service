# Identity Service

Identity and Access Management microservice for the Travel Management System. Handles user registration, authentication, role-based access control, password management, email verification, and asynchronous event publishing via the Transactional Outbox Pattern.

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 25 |
| Framework | Spring Boot 4.1.1 (Web MVC) |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Security | Spring Security Crypto (BCrypt), JWT |
| Messaging | Apache Kafka (optional, Outbox Pattern) |
| Email | Spring Boot Starter Mail (SMTP) |
| Validation | Jakarta Bean Validation |
| Build | Maven 3.9 |
| Containerisation | Docker / Docker Compose |

## Capabilities

| Feature | Description |
|---|---|
| User registration | Email, password, first/last name. Default role: TRAVELER |
| Email verification | Token-based, single-use, 1-hour expiry. SMTP delivery or log fallback |
| Authentication | Access token (15 min) + refresh token (7 days, DB-backed) |
| Token refresh | Rotation on every refresh. Old token revoked. |
| Logout | Idempotent refresh token revocation |
| Password change | Requires current password verification |
| Password reset | Forgot/reset flow with time-limited, single-use tokens |
| Role-based access | ADMIN, TRAVEL_MANAGER, TRAVELER roles with permission associations |
| Outbox events | UserRegisteredEvent, UserUpdatedEvent. Durable storage, Kafka relay |
| API versioning | Header-based (`X-API-Version`), defaults to 1 |

## Technical Decisions

| Decision | Rationale |
|---|---|
| Access + Refresh tokens | Access token for stateless auth, refresh token for long-lived sessions without exposing credentials repeatedly |
| Refresh tokens hashed with SHA-256 | Raw tokens are never persisted. Only the hash is stored in the database |
| DB-backed verification and reset tokens | Tokens are stored with expiry and single-use enforcement. No in-memory state |
| Outbox Pattern for Kafka events | Events are written to `outbox_events` in the same transaction as the business record. A relay polls and publishes to Kafka. No events are lost on broker failure |
| Email verification via SMTP | Verification tokens are generated on registration and sent via SMTP. A `LoggingEmailService` fallback logs the token when SMTP is disabled (dev mode) |
| Rate limiting at gateway level | Login, refresh, forgot-password, and verify-email are rate-limited at the API Gateway, not in this service |
| Testcontainers for integration tests | All integration tests spin up an isolated PostgreSQL instance. No local database required to run tests |

## API Endpoints

| Endpoint | Method | Description |
|---|---|---|
| `/auth/register` | POST | Register a new user |
| `/auth/login` | POST | Authenticate and get tokens |
| `/auth/refresh` | POST | Rotate refresh token |
| `/auth/logout` | POST | Revoke refresh token |
| `/auth/change-password` | POST | Change password (requires `X-User-Id` header) |
| `/auth/forgot-password` | POST | Request a password reset token |
| `/auth/reset-password` | POST | Reset password with token |
| `/auth/verify-email` | GET | Verify email with token |

For curl commands, Postman/Insomnia setup, and request/response examples, see [docs/API_TEST_GUIDE.md](docs/API_TEST_GUIDE.md).

## Project Structure

```
identity_service/
├── src/main/java/io/github/johneliud/identity_service/
│   ├── config/          # JwtTokenProvider, WebMvcConfig
│   ├── controller/      # RegisterController, LoginController, TokenController, PasswordController, EmailVerificationController
│   ├── dto/             # Request/Response DTOs with validation
│   ├── event/           # OutboxEventPublisher, KafkaUserEventPublisher, OutboxEventRelayService
│   ├── exception/       # Custom exceptions and GlobalExceptionHandler
│   ├── interceptor/     # API versioning interceptor
│   ├── model/           # JPA entities (User, Role, RefreshToken, ResetToken, VerificationToken)
│   ├── repository/      # Spring Data JPA repositories
│   └── service/         # RegistrationService, LoginService, TokenService, PasswordService, EmailVerificationService, EmailService
├── src/main/resources/
│   ├── application.yml
│   ├── application-secrets.properties
│   └── db/migration/    # Flyway SQL scripts (V1 through V7)
├── config/
│   └── application-identity-service.yml  # Gateway route config
├── docs/
│   └── API_TEST_GUIDE.md
└── pom.xml
```

## Getting Started

### Prerequisites

- Java 25
- Maven 3.9+
- Docker and Docker Compose
- PostgreSQL (or use Docker Compose to manage it)

### Run with Docker Compose

Starts PostgreSQL and the Identity Service. Migrations run automatically.

```bash
docker compose up --build -d

# Follow service logs
docker compose logs -f identity-service

# Stop (preserves database volume)
docker compose down

# Clean restart (removes database volume)
docker compose down -v
```

### Run Locally (Database via Docker, Service via Maven)

```bash
# Start only PostgreSQL (exposed on port 5433)
docker compose up -d postgres

# Run the service
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/identity_db \
SPRING_DATASOURCE_USERNAME=postgres \
SPRING_DATASOURCE_PASSWORD=postgres \
./mvnw spring-boot:run
```

### Run with Local PostgreSQL

```bash
# Create the database
createdb identity_db

# Run the service
SPRING_DATASOURCE_PASSWORD=postgres ./mvnw spring-boot:run
```

## Configuration

Configuration is loaded in this priority order:

1. Command-line arguments
2. Environment variables
3. `application-secrets.properties` (git-ignored, for local dev)
4. `application.yml` (defaults)

### Environment Variables

| Variable | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8081` | HTTP port |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/identity_db` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | (required) | Database password |
| `JWT_SECRET` | (required) | Secret for signing JWTs |
| `JWT_ACCESS_TOKEN_EXPIRATION` | `900000` | Access token TTL (ms) |
| `JWT_REFRESH_TOKEN_EXPIRATION` | `604800000` | Refresh token TTL (ms) |
| `REQUIRE_EMAIL_VERIFICATION` | `true` | Set to `false` to skip email verification |
| `EMAIL_ENABLED` | `false` | Enable SMTP email sending |
| `MAIL_HOST` | `localhost` | SMTP server host |
| `MAIL_PORT` | `587` | SMTP server port |
| `MAIL_USERNAME` | (empty) | SMTP authentication username |
| `MAIL_PASSWORD` | (empty) | SMTP authentication password |
| `EMAIL_FROM_ADDRESS` | `noreply@localhost` | Sender address for verification emails |
| `EMAIL_FRONTEND_BASE_URL` | `http://localhost:4200` | Base URL for verification links |
| `EMAIL_VERIFICATION_TOKEN_EXPIRATION_MS` | `3600000` | Verification token TTL (ms) |
| `DEV_INCLUDE_VERIFICATION_TOKEN_IN_RESPONSE` | `true` | Include token in register response (dev only) |
| `KAFKA_ENABLED` | `false` | Enable Kafka event publishing |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `KAFKA_TOPIC_USER_REGISTERED` | `user.registered` | Topic for UserRegisteredEvent |
| `KAFKA_TOPIC_USER_UPDATED` | `user.updated` | Topic for UserUpdatedEvent |
| `OUTBOX_RELAY_FIXED_DELAY_MS` | `10000` | Delay between outbox relay runs (ms) |
| `OUTBOX_RELAY_BATCH_SIZE` | `50` | Maximum events per relay run |

See `application-secrets.properties` for a complete local dev config file with safe defaults.

## Data Model

| Table | Description |
|---|---|
| `users` | User accounts with email, password hash, status, email_verified |
| `roles` | Role definitions (ADMIN, TRAVEL_MANAGER, TRAVELER) |
| `permissions` | Permission definitions |
| `user_roles` | User-to-role join table |
| `role_permissions` | Role-to-permission join table |
| `refresh_tokens` | DB-backed refresh tokens (hashed, single-use, rotatable) |
| `reset_tokens` | Password reset tokens (hashed, single-use, 1-hour expiry) |
| `verification_tokens` | Email verification tokens (hashed, single-use, 1-hour expiry) |
| `outbox_events` | Outbox events for Kafka relay (unpublished flag with partial index) |

## Database Migrations

Flyway runs automatically on startup. Scripts live in `src/main/resources/db/migration/`.

| Version | Description |
|---|---|
| V1 | Creates users, roles, permissions, user_roles, role_permissions; indexes; updated_at triggers |
| V2 | Seeds default roles, permissions, and role-permission associations |
| V3 | Adds first_name and last_name columns to users |
| V4 | Creates outbox_events table and polling index |
| V5 | Creates refresh_tokens table |
| V6 | Creates reset_tokens table |
| V7 | Creates verification_tokens table |

To add a new migration, create a file following the naming convention `V<next_version>__<short_description>.sql`.

## Testing

Tests use Testcontainers to spin up an isolated PostgreSQL instance. No local database is required.

```bash
./mvnw clean test
```

### Test Coverage

| Test Class | Type | What It Covers |
|---|---|---|
| RegistrationServiceTest | Unit | Registration business logic |
| LoginServiceTest | Unit | Login, credential validation, account status checks |
| TokenServiceTest | Unit | Token refresh, rotation, logout |
| PasswordServiceTest | Unit | Change/forgot/reset password flows |
| EmailVerificationServiceTest | Unit | Email verification token generation and verification |
| TokenHashUtilTest | Unit | SHA-256 token hashing |
| AuthRegistrationIntegrationTest | Integration | End-to-end registration via HTTP |
| AuthLoginIntegrationTest | Integration | End-to-end login via HTTP |
| AuthRefreshLogoutIntegrationTest | Integration | End-to-end refresh and logout via HTTP |
| EmailVerificationIntegrationTest | Integration | End-to-end email verification flow |
| DatabaseMigrationTests | Integration | Verifies all Flyway migrations apply cleanly |
| KafkaUserEventPublisherTest | Unit | Kafka publisher behavior (enabled/disabled) |

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
