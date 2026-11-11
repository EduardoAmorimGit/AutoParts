-- One row per payment attempt of an order (an order may have a rejected card attempt followed by
-- an approved Pix). The provider's status is the source of truth; webhooks only trigger a re-read.
CREATE TABLE payments (
    id                     BIGSERIAL PRIMARY KEY,
    order_id               BIGINT         NOT NULL,
    provider               VARCHAR(20)    NOT NULL,
    method                 VARCHAR(20)    NOT NULL,
    status                 VARCHAR(20)    NOT NULL,
    amount                 NUMERIC(12, 2) NOT NULL,
    idempotency_key        VARCHAR(64)    NOT NULL,
    provider_payment_id    VARCHAR(64),
    provider_status        VARCHAR(40),
    provider_status_detail VARCHAR(80),
    pix_qr_code            TEXT,
    pix_qr_code_base64     TEXT,
    pix_ticket_url         VARCHAR(500),
    expires_at             TIMESTAMPTZ,
    approved_at            TIMESTAMPTZ,
    refunded_at            TIMESTAMPTZ,
    needs_attention        BOOLEAN        NOT NULL DEFAULT FALSE,
    attention_reason       VARCHAR(200),
    version                BIGINT         NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_payments_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_payments_provider CHECK (provider IN ('MERCADO_PAGO')),
    CONSTRAINT ck_payments_method CHECK (method IN ('PIX', 'CREDIT_CARD')),
    CONSTRAINT ck_payments_status CHECK (status IN
        ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'REFUNDED', 'CHARGED_BACK')),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_payments_provider_payment ON payments (provider, provider_payment_id)
    WHERE provider_payment_id IS NOT NULL;
CREATE INDEX idx_payments_order ON payments (order_id);
CREATE INDEX idx_payments_attention ON payments (needs_attention) WHERE needs_attention;
