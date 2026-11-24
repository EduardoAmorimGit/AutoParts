-- After-delivery returns: withdrawal (CDC art. 49, 7 days, no reason needed) and defects (CDC art. 18).
CREATE TABLE return_requests (
    id                    BIGSERIAL PRIMARY KEY,
    order_id              BIGINT        NOT NULL REFERENCES orders (id),
    user_id               BIGINT        NOT NULL REFERENCES users (id),
    type                  VARCHAR(20)   NOT NULL,
    status                VARCHAR(20)   NOT NULL,
    customer_reason       VARCHAR(1000),
    items_amount          NUMERIC(12, 2) NOT NULL,
    delivery_fee_amount   NUMERIC(10, 2) NOT NULL DEFAULT 0,
    refund_amount         NUMERIC(12, 2) NOT NULL,
    admin_note            VARCHAR(1000),
    rejection_reason      VARCHAR(1000),
    restocked             BOOLEAN       NOT NULL DEFAULT FALSE,
    decided_by            BIGINT REFERENCES users (id),
    requested_at          TIMESTAMPTZ   NOT NULL,
    approved_at           TIMESTAMPTZ,
    received_at           TIMESTAMPTZ,
    resolved_at           TIMESTAMPTZ,
    version               BIGINT        NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_return_requests_type CHECK (type IN ('WITHDRAWAL', 'DEFECT')),
    CONSTRAINT ck_return_requests_status
        CHECK (status IN ('REQUESTED', 'APPROVED', 'RECEIVED', 'REFUNDED', 'REPLACED', 'REJECTED'))
);

CREATE INDEX idx_return_requests_order ON return_requests (order_id);
CREATE INDEX idx_return_requests_status ON return_requests (status, requested_at);

CREATE TABLE return_request_items (
    id                BIGSERIAL PRIMARY KEY,
    return_request_id BIGINT  NOT NULL REFERENCES return_requests (id) ON DELETE CASCADE,
    order_item_id     BIGINT  NOT NULL REFERENCES order_items (id),
    quantity          INTEGER NOT NULL CHECK (quantity > 0)
);

-- Every partial refund sent to the provider, with the idempotency key reused on retries so a
-- timeout followed by a retry never refunds twice.
CREATE TABLE refunds (
    id                 BIGSERIAL PRIMARY KEY,
    order_id           BIGINT        NOT NULL REFERENCES orders (id),
    payment_id         BIGINT        NOT NULL REFERENCES payments (id),
    return_request_id  BIGINT REFERENCES return_requests (id),
    amount             NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    status             VARCHAR(20)   NOT NULL,
    idempotency_key    VARCHAR(64)   NOT NULL,
    provider_refund_id VARCHAR(64),
    last_error         VARCHAR(200),
    created_by         BIGINT REFERENCES users (id),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    completed_at       TIMESTAMPTZ,
    CONSTRAINT uq_refunds_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_refunds_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX idx_refunds_payment ON refunds (payment_id);
CREATE UNIQUE INDEX uq_refunds_return_request ON refunds (return_request_id) WHERE return_request_id IS NOT NULL;

ALTER TABLE orders ADD COLUMN refunded_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;
