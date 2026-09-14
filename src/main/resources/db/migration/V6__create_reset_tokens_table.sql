-- Description: Create reset_tokens table for password reset flow
CREATE TABLE reset_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash  VARCHAR(255) NOT NULL,
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used        BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_reset_tokens_token_hash UNIQUE (token_hash)
);

-- Index to efficiently look up valid reset tokens
CREATE INDEX idx_reset_tokens_token_hash ON reset_tokens (token_hash) WHERE used = FALSE;
CREATE INDEX idx_reset_tokens_user_id ON reset_tokens (user_id);
