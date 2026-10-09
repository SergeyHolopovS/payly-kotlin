CREATE TABLE payments (
    id               VARCHAR(64) PRIMARY KEY,
    merchant_id      UUID NOT NULL REFERENCES merchants (id),
    amount           BIGINT NOT NULL,
    currency         VARCHAR(3) NOT NULL,
    status           VARCHAR(32) NOT NULL,
    description      TEXT,
    metadata         JSONB NOT NULL DEFAULT '{}'::jsonb,
    card_last4       VARCHAR(4) NOT NULL,
    failure_reason   TEXT,
    refunded_amount  BIGINT NOT NULL DEFAULT 0,
    version          BIGINT NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_merchant_id ON payments (merchant_id);
