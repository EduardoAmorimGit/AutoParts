-- Every customer e-mail is written here in the same transaction as the change it reports, then
-- sent by a job. The stored copy (recipient, subject, body, send time) is the proof of what the
-- store communicated and when.
CREATE TABLE email_outbox (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT REFERENCES users (id),
    order_id      BIGINT REFERENCES orders (id),
    kind          VARCHAR(40)  NOT NULL,
    to_address    VARCHAR(255) NOT NULL,
    subject       VARCHAR(200) NOT NULL,
    body          TEXT         NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    attempts      INTEGER      NOT NULL DEFAULT 0,
    last_error    VARCHAR(500),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_email_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX idx_email_outbox_pending ON email_outbox (status, next_attempt_at);
CREATE INDEX idx_email_outbox_order ON email_outbox (order_id);
