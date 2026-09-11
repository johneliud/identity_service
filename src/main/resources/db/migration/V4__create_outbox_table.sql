-- Description: Create outbox table for transactional outbox pattern
CREATE TABLE outbox_events (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type  VARCHAR(255) NOT NULL,
    payload     TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published   BOOLEAN     NOT NULL DEFAULT FALSE
);

-- Index to efficiently poll for unpublished events
CREATE INDEX idx_outbox_events_published ON outbox_events (published) WHERE published = FALSE;
