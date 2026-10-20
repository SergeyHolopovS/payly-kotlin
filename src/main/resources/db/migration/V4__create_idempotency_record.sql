CREATE TABLE idempotency_records (
    id               UUID PRIMARY KEY,
    merchant_id      UUID NOT NULL,
    idempotency_key  VARCHAR(255) NOT NULL,
    request_hash     VARCHAR(128) NOT NULL,
    response_status  INTEGER NOT NULL,
    response_body    TEXT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_idempotency_records_merchant_key UNIQUE (merchant_id, idempotency_key)
);
