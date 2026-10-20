CREATE TABLE webhook_deliveries (
    id                 UUID PRIMARY KEY,
    merchant_id        UUID NOT NULL REFERENCES merchants (id),
    event_type         VARCHAR(128) NOT NULL,
    payload            TEXT NOT NULL,
    status             VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempts           INTEGER NOT NULL DEFAULT 0,
    next_attempt_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_response_code INTEGER,
    last_error         TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_webhook_deliveries_merchant_id ON webhook_deliveries (merchant_id);

-- Для будущего ретрай-планировщика: выборка "что ещё доставить" по статусу
-- и сроку следующей попытки, аналогично idx_outbox_events_published_at.
CREATE INDEX idx_webhook_deliveries_status_next_attempt
    ON webhook_deliveries (status, next_attempt_at);
