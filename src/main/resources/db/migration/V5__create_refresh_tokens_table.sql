-- Description: Create refresh_tokens table for JWT refresh token persistence
CREATE TABLE refresh_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash  VARCHAR(255) NOT NULL,
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked     BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
);

-- Index to efficiently look up valid refresh tokens
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens (token_hash) WHERE revoked = FALSE;
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
