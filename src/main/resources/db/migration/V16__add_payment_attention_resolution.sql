-- Admins close a flagged payment (needs_attention) after handling it, with a note for the record.
ALTER TABLE payments ADD COLUMN resolved_at TIMESTAMPTZ;
ALTER TABLE payments ADD COLUMN resolved_by BIGINT REFERENCES users (id);
ALTER TABLE payments ADD COLUMN resolution_note VARCHAR(500);
