ALTER TABLE outbox_events
    ADD COLUMN claimed_at       TIMESTAMPTZ,
    ADD COLUMN claim_expires_at TIMESTAMPTZ,
    ADD COLUMN claimed_by       VARCHAR(200),
    ADD COLUMN claim_token      UUID,
    ADD COLUMN attempt_count    INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_outbox_events_claimable
    ON outbox_events (created_at, claim_expires_at)
    WHERE published_at IS NULL;