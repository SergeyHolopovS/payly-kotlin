CREATE TABLE merchants (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    api_key_hash    VARCHAR(255) NOT NULL UNIQUE,
    webhook_url     VARCHAR(2048),
    webhook_secret  VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
