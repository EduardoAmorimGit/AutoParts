-- Fulfillment data filled in by the admins from the operations panel.
ALTER TABLE orders ADD COLUMN paid_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN shipped_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN delivered_at TIMESTAMPTZ;
-- Courier tracking link (Lalamove, pasted by hand in phase 1).
ALTER TABLE orders ADD COLUMN tracking_url VARCHAR(500);
