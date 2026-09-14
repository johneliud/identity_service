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
9. [Change User Role](#9-change-user-role)
10. [List Users (Admin)](#10-list-users-admin)
11. [Get User Detail (Admin)](#11-get-user-detail-admin)
12. [Update User Status (Admin)](#12-update-user-status-admin)

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

Changes the password for an authenticated user. This endpoint requires a valid JWT access token in the `Authorization` header. The gateway validates the token and automatically injects the `X-User-Id` header before forwarding to the identity service.

You must first login (see [2. Login](#2-login)) to obtain an access token.

### curl

```bash
curl -X POST http://localhost:8080/auth/change-password \
  -H "Content-Type: application/json" \
  -H "X-API-Version: 1" \
  -H "Authorization: Bearer <your-access-token>" \
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
   - `Authorization: Bearer <your-access-token>`
4. The access token is obtained from the login response
5. Body, raw, JSON:

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
| 401 Unauthorized | Missing or invalid `Authorization` header |
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

## 9. Change User Role

```
PATCH /users/{id}/roles
```

Adds or removes a role from a user. Requires ADMIN role. This endpoint is protected at two levels:
- **Gateway**: The `AuthorizationFilter` checks that the caller has the `ADMIN` role before the request reaches the identity service.
- **Identity service**: `@PreAuthorize("hasRole('ADMIN')")` on the service method provides defense in depth.

You must first login as an ADMIN user (see [2. Login](#2-login)) to obtain an access token.

### curl

```bash
curl -X PATCH http://localhost:8080/users/USER_UUID_HERE/roles \
  -H "Content-Type: application/json" \
  -H "X-API-Version: 1" \
  -H "Authorization: Bearer <admin-access-token>" \
  -d '{
    "roleName": "TRAVEL_MANAGER",
    "action": "ADD"
  }'
```

### Postman / Insomnia

1. Method: `PATCH`
2. URL: `http://localhost:8080/users/{id}/roles`
3. Headers:
   - `Content-Type: application/json`
   - `X-API-Version: 1`
4. Auth tab: Select "Bearer Token" and paste the admin access token
5. Body, raw, JSON:

```json
{
  "roleName": "TRAVEL_MANAGER",
  "action": "ADD"
}
```

### Request Body

| Field | Type | Required | Values |
|---|---|---|---|
| `roleName` | String | Yes | `ADMIN`, `TRAVEL_MANAGER`, `TRAVELER` |
| `action` | String | Yes | `ADD`, `REMOVE` |

### Success Response: 204 No Content

No body.

### Error Responses

| Status | Condition |
|---|---|
| 400 Bad Request | Validation failure (invalid role name or action) |
| 401 Unauthorized | Missing or invalid `Authorization` header |
| 403 Forbidden | Caller does not have ADMIN role |
| 404 Not Found | User not found |
| 409 Conflict | User already has the role (for ADD) or does not have the role (for REMOVE) |

---

## 10. List Users (Admin)

```
GET /admin/users
```

Returns a paginated list of users. Supports filtering by status, role, and email. Requires ADMIN role.

### Query Parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `page` | int | `0` | Page number (0-indexed) |
| `size` | int | `20` | Page size |
| `status` | String | - | Filter by status: `ACTIVE`, `PENDING`, `DEACTIVATED` |
| `role` | String | - | Filter by role name: `ADMIN`, `TRAVEL_MANAGER`, `TRAVELER` |
| `email` | String | - | Filter by email (partial match, case-insensitive) |

### curl

```bash
curl -X GET "http://localhost:8080/admin/users?page=0&size=10&status=ACTIVE" \
  -H "X-API-Version: 1" \
  -H "Authorization: Bearer <admin-access-token>"
```

### Postman / Insomnia

1. Method: `GET`
2. URL: `http://localhost:8080/admin/users?page=0&size=10`
3. Headers:
   - `X-API-Version: 1`
   - `Authorization: Bearer <admin-access-token>`
4. The admin access token is obtained from the login response.

### Success Response: 200 OK

```json
{
  "content": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "email": "user@example.com",
      "firstName": "Test",
      "lastName": "User",
      "status": "ACTIVE",
      "emailVerified": true,
      "roles": ["TRAVELER"],
      "createdAt": "2026-01-01T00:00:00Z"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

### Error Responses

| Status | Condition |
|---|---|
| 401 Unauthorized | Missing or invalid `Authorization` header |
| 403 Forbidden | Caller does not have ADMIN role |

---

## 11. Get User Detail (Admin)

```
GET /admin/users/{id}
```

Returns full user details (excluding password hash). Requires ADMIN role.

### curl

```bash
curl -X GET http://localhost:8080/admin/users/USER_UUID_HERE \
  -H "X-API-Version: 1" \
  -H "Authorization: Bearer <admin-access-token>"
```

### Postman / Insomnia

1. Method: `GET`
2. URL: `http://localhost:8080/admin/users/{id}`
3. Headers:
   - `X-API-Version: 1`
4. Auth tab: Select "Bearer Token" and paste the admin access token

### Success Response: 200 OK

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "firstName": "Test",
  "lastName": "User",
  "status": "ACTIVE",
  "emailVerified": true,
  "roles": ["TRAVELER"],
  "createdAt": "2026-01-01T00:00:00Z",
  "updatedAt": "2026-01-01T00:00:00Z"
}
```

### Error Responses

| Status | Condition |
|---|---|
| 401 Unauthorized | Missing or invalid `Authorization` header |
| 403 Forbidden | Caller does not have ADMIN role |
| 404 Not Found | User not found |

---

## 12. Update User Status (Admin)

```
PATCH /admin/users/{id}/status
```

Activates or deactivates a user account. Requires ADMIN role. Emits `UserUpdatedEvent` with `changeType=USER_DEACTIVATED` or `USER_REACTIVATED`.

### curl

```bash
curl -X PATCH http://localhost:8080/admin/users/USER_UUID_HERE/status \
  -H "Content-Type: application/json" \
  -H "X-API-Version: 1" \
  -H "Authorization: Bearer <admin-access-token>" \
  -d '{
    "status": "DEACTIVATED"
  }'
```

### Postman / Insomnia

1. Method: `PATCH`
2. URL: `http://localhost:8080/admin/users/{id}/status`
3. Headers:
   - `Content-Type: application/json`
   - `X-API-Version: 1`
4. Auth tab: Select "Bearer Token" and paste the admin access token
5. Body, raw, JSON:

```json
{
  "status": "DEACTIVATED"
}
```

### Request Body

| Field | Type | Required | Values |
|---|---|---|---|
| `status` | String | Yes | `ACTIVE`, `DEACTIVATED` |

### Success Response: 204 No Content

No body.

### Error Responses

| Status | Condition |
|---|---|
| 400 Bad Request | Invalid status value |
| 401 Unauthorized | Missing or invalid `Authorization` header |
| 403 Forbidden | Caller does not have ADMIN role |
| 404 Not Found | User not found |

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
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Str0ng!Pass1"
  }' | jq -r '.accessToken')

# 5. Use the access token for authenticated requests
curl -s -X POST http://localhost:8080/auth/change-password \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "currentPassword": "Str0ng!Pass1",
    "newPassword": "N3w!Str0ngPass"
  }' | jq .
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
