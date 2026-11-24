-- Proof of what the customer agreed to: version of the terms/returns/privacy documents accepted at
-- checkout and when (orders placed before this existed keep NULL).
ALTER TABLE orders ADD COLUMN terms_version VARCHAR(20);
ALTER TABLE orders ADD COLUMN terms_accepted_at TIMESTAMPTZ;
