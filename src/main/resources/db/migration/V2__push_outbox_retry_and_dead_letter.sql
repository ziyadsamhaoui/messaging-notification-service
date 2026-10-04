ALTER TABLE pending_push_deliveries
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN last_error TEXT,
    ADD COLUMN next_retry_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

DROP INDEX ix_pending_push_undelivered;

CREATE INDEX ix_pending_push_pending ON pending_push_deliveries (status, next_retry_at, created_at)
    WHERE status = 'PENDING';
