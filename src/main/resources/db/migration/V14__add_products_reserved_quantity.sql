-- stock_quantity becomes the physical count (what is on the shelf). Units of open orders
-- (awaiting payment, paid, preparing) are tracked in reserved_quantity; available for sale is
-- stock_quantity - reserved_quantity. Until now checkout subtracted open orders from stock_quantity,
-- so they are added back here and recorded as reserved.
ALTER TABLE products ADD COLUMN reserved_quantity INTEGER NOT NULL DEFAULT 0;

UPDATE products p
SET reserved_quantity = open_items.quantity,
    stock_quantity    = p.stock_quantity + open_items.quantity
FROM (SELECT oi.product_id, SUM(oi.quantity) AS quantity
      FROM order_items oi
      JOIN orders o ON o.id = oi.order_id
      WHERE o.status IN ('AWAITING_PAYMENT', 'PAID', 'PREPARING')
        AND oi.product_id IS NOT NULL
      GROUP BY oi.product_id) open_items
WHERE p.id = open_items.product_id;

ALTER TABLE products ADD CONSTRAINT ck_products_reserved_quantity CHECK (reserved_quantity >= 0);
