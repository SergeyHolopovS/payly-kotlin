CREATE TABLE refunds (
    id          VARCHAR(64) PRIMARY KEY,
    payment_id  VARCHAR(64) NOT NULL REFERENCES payments (id),
    amount      BIGINT NOT NULL,
    status      VARCHAR(32) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refunds_payment_id ON refunds (payment_id);
