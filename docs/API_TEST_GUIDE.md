# API Test Guide

Every endpoint in the Identity Service with curl commands and Postman/Insomnia setup instructions.

All requests go through the API Gateway on `http://localhost:8080`. If you are testing the Identity Service directly (without the gateway), use `http://localhost:8081` instead.

---

## Table of Contents

1. [Register User](#1-register-user)
2. [Login](#2-login)
3. [Refresh Token](#3-refresh-token)
4. [Logout](#4-logout)
5. [Change Password](#5-change-password)
6. [Forgot Password](#6-forgot-password)
7. [Reset Password](#7-reset-password)
8. [Verify Email](#8-verify-email)

---

## Common Headers

Every request should include:

```
Content-Type: application/json
X-API-Version: 1
```

The `X-API-Version` header defaults to `1` if omitted. The resolved version is echoed back in the `X-API-Version` response header.

---

## 1. Register User

```
POST /auth/register
```

Creates a new user account. When email verification is enabled (default), the user is created with status `PENDING` and `emailVerified: false`.

### curl

```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -H "X-API-Version: 1" \
  -d '{
    "email": "jane.doe@example.com",
    "password": "Str0ng!Pass1",
    "firstName": "Jane",
    "lastName": "Doe"
  }'
```

Without last name (optional field):

```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "jane.doe@example.com",
    "password": "Str0ng!Pass1",
    "firstName": "Jane"
  }'
```

### Postman / Insomnia

1. Method: `POST`
2. URL: `http://localhost:8080/auth/register`
3. Headers:
   - `Content-Type: application/json`
   - `X-API-Version: 1` (optional, defaults to 1)
4. Body, raw, JSON:

```json
{
  "email": "jane.doe@example.com",
  "password": "Str0ng!Pass1",
  "firstName": "Jane",
  "lastName": "Doe"
}
```

### Success Response: 201 Created

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "jane.doe@example.com",
  "firstName": "Jane",
  "lastName": "Doe",
  "status": "PENDING",
  "emailVerified": false,
  "roles": ["TRAVELER"],
  "verificationToken": "abc123...",
  "createdAt": "2026-09-14T10:00:00Z",
  "updatedAt": "2026-09-14T10:00:00Z"
}
```

> The `verificationToken` field is only present when `identity.dev.include-verification-token-in-response=true` (default for local dev). It is not included in production.

### Error Responses

| Status | Condition |
|---|---|
| 400 Bad Request | Validation failure (missing fields, weak password, invalid email) |
| 400 Bad Request | Unsupported `X-API-Version` header |
| 409 Conflict | Email address already registered |

---

## 2. Login

```
POST /auth/login
```

Authenticates a user and returns an access token and a refresh token.

### curl

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -H "X-API-Version: 1" \
  -d '{
    "email": "jane.doe@example.com",
    "password": "Str0ng!Pass1"
  }'
```

### Postman / Insomnia

1. Method: `POST`
2. URL: `http://localhost:8080/auth/login`
3. Headers:
   - `Content-Type: application/json`
   - `X-API-Version: 1`
4. Body, raw, JSON:

```json
{
  "email": "jane.doe@example.com",
  "password": "Str0ng!Pass1"
}
```

### Success Response: 200 OK

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2g...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

### Error Responses

| Status | Condition |
|---|---|
| 401 Unauthorized | Invalid email or password |
| 403 Forbidden | Account has been deactivated |
| 403 Forbidden | Email address has not been verified |

---

## 3. Refresh Token

```
POST /auth/refresh
```

Rotates the refresh token. The old refresh token is revoked and a new pair (access + refresh) is issued.

### curl

```bash
curl -X POST http://localhost:8080/auth/refresh \
  -H "Content-Type: application/json" \
  -H "X-API-Version: 1" \
  -d '{
    "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2g..."
  }'
```

### Postman / Insomnia

1. Method: `POST`
2. URL: `http://localhost:8080/auth/refresh`
3. Headers:
   - `Content-Type: application/json`
   - `X-API-Version: 1`
4. Body, raw, JSON:

```json
{
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2g..."
}
```

### Success Response: 200 OK

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "bmV3IHJlZnJlc2ggdG9rZW4...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

### Error Responses

| Status | Condition |
|---|---|
| 401 Unauthorized | Invalid or revoked refresh token |
| 401 Unauthorized | Refresh token has expired |
| 403 Forbidden | Account has been deactivated |

---

## 4. Logout

```
POST /auth/logout
```

Revokes the refresh token. Idempotent: calling logout with an already-revoked or non-existent token returns 204.

### curl

```bash
curl -X POST http://localhost:8080/auth/logout \
  -H "Content-Type: application/json" \
  -H "X-API-Version: 1" \
  -d '{
    "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2g..."
  }'
```

### Postman / Insomnia

1. Method: `POST`
2. URL: `http://localhost:8080/auth/logout`
3. Headers:
   - `Content-Type: application/json`
   - `X-API-Version: 1`
4. Body, raw, JSON:

```json
{
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2g..."
}
```

### Success Response: 204 No Content

No body.

---

## 5. Change Password

```
POST /auth/change-password
```

Changes the password for an authenticated user. Requires the `X-User-Id` header (injected by the gateway's authentication filter).

### curl

```bash
curl -X POST http://localhost:8080/auth/change-password \
  -H "Content-Type: application/json" \
  -H "X-API-Version: 1" \
  -H "X-User-Id: 550e8400-e29b-41d4-a716-446655440000" \
  -d '{
    "currentPassword": "Str0ng!Pass1",
    "newPassword": "N3w!Str0ngPass"
  }'
```

### Postman / Insomnia

1. Method: `POST`
2. URL: `http://localhost:8080/auth/change-password`
3. Headers:
   - `Content-Type: application/json`
   - `X-API-Version: 1`
   - `X-User-Id: 550e8400-e29b-41d4-a716-446655440000` (your user UUID)
4. Body, raw, JSON:

```json
{
  "currentPassword": "Str0ng!Pass1",
  "newPassword": "N3w!Str0ngPass"
}
```

### Success Response: 204 No Content

No body.

### Error Responses

| Status | Condition |
|---|---|
| 400 Bad Request | Validation failure |
| 401 Unauthorized | Current password is incorrect |
| 401 Unauthorized | New password must be different from current password |
| 403 Forbidden | Account has been deactivated |

---

## 6. Forgot Password

```
POST /auth/forgot-password
```

Generates a password reset token for the given email. Always returns 204 regardless of whether the email exists (prevents email enumeration).

### curl

```bash
curl -X POST http://localhost:8080/auth/forgot-password \
  -H "Content-Type: application/json" \
  -H "X-API-Version: 1" \
  -d '{
    "email": "jane.doe@example.com"
  }'
```

### Postman / Insomnia

1. Method: `POST`
2. URL: `http://localhost:8080/auth/forgot-password`
3. Headers:
   - `Content-Type: application/json`
   - `X-API-Version: 1`
4. Body, raw, JSON:

```json
{
  "email": "jane.doe@example.com"
}
```

### Success Response: 204 No Content

No body. The reset token is logged to the server console (check identity service logs). In a production environment, it would be sent via email.

---

## 7. Reset Password

```
POST /auth/reset-password
```

Resets the password using a valid reset token. The token is single-use and expires after 1 hour.

### curl

```bash
curl -X POST http://localhost:8080/auth/reset-password \
  -H "Content-Type: application/json" \
  -H "X-API-Version: 1" \
  -d '{
    "token": "raw-reset-token-from-email-or-logs",
    "newPassword": "R3s3t!Pass1"
  }'
```

### Postman / Insomnia

1. Method: `POST`
2. URL: `http://localhost:8080/auth/reset-password`
3. Headers:
   - `Content-Type: application/json`
   - `X-API-Version: 1`
4. Body, raw, JSON:

```json
{
  "token": "raw-reset-token-from-email-or-logs",
  "newPassword": "R3s3t!Pass1"
}
```

### Success Response: 204 No Content

No body.

### Error Responses

| Status | Condition |
|---|---|
| 400 Bad Request | Invalid or already used reset token |
| 400 Bad Request | Reset token has expired |
| 403 Forbidden | Account has been deactivated |

---

## 8. Verify Email

```
GET /auth/verify-email?token=...
```

Verifies a user's email address. Activates the account by setting status to `ACTIVE` and `emailVerified` to `true`.

### curl

```bash
curl -X GET "http://localhost:8080/auth/verify-email?token=abc123def456" \
  -H "X-API-Version: 1"
```

### Postman / Insomnia

1. Method: `GET`
2. URL: `http://localhost:8080/auth/verify-email?token=abc123def456`
3. Headers:
   - `X-API-Version: 1`
4. No body required.

### Success Response: 200 OK

No body. The user's status changes from `PENDING` to `ACTIVE` and `emailVerified` becomes `true`.

### Error Responses

| Status | Condition |
|---|---|
| 400 Bad Request | Missing `token` query parameter |
| 400 Bad Request | Invalid or already used verification token |
| 400 Bad Request | Verification token has expired |
| 403 Forbidden | Account has been deactivated |

---

## Complete Workflow Example

This walks through the full registration-to-login flow using curl.

```bash
# 1. Register
curl -s -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Str0ng!Pass1",
    "firstName": "Test",
    "lastName": "User"
  }' | jq .

# 2. Get the verification token from the response (dev mode only)
# or check the identity service logs

# 3. Verify email
curl -s -X GET "http://localhost:8080/auth/verify-email?token=YOUR_TOKEN_HERE" \
  -H "X-API-Version: 1" \
  -w "\nHTTP Status: %{http_code}\n"

# 4. Login
curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Str0ng!Pass1"
  }' | jq .

# 5. Use the access token for authenticated requests
# The gateway injects X-User-Id and X-User-Roles headers for downstream services
```

---

## Troubleshooting

| Issue | Solution |
|---|---|
| Connection refused on port 8080 | Make sure the API Gateway is running |
| Connection refused on port 8081 | Make sure the Identity Service is running |
| 400 "Unsupported API version" | Check that `X-API-Version: 1` header is set |
| 401 "Invalid email or password" | Verify credentials. Check that the account is not deactivated or unverified |
| 403 "Email address has not been verified" | Complete the email verification step before logging in |
| 429 Too Many Requests | Rate limit exceeded. Wait for the refill interval (default: 15 minutes) |
| 500 "An unexpected internal error occurred" | Check identity service logs for the root cause |
