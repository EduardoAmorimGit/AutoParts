-- Single-use password reset links. Only the SHA-256 of the token is stored; the token itself
-- travels once, in the e-mail.
CREATE TABLE password_reset_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens (user_id, created_at);

-- E-mails that carry a secret (reset links) have their body replaced once sent or given up,
-- and the admin listing never shows it.
ALTER TABLE email_outbox ADD COLUMN sensitive BOOLEAN NOT NULL DEFAULT FALSE;
