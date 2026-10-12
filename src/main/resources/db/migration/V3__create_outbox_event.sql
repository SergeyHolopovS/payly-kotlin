CREATE TABLE outbox_events (
    id            UUID PRIMARY KEY,
    aggregate_id  VARCHAR(64) NOT NULL,
    event_type    VARCHAR(128) NOT NULL,
    payload       JSONB NOT NULL DEFAULT '{}'::jsonb,
    published_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_outbox_events_published_at ON outbox_events (published_at);
