-- Delivery addresses of a customer (personal data: only the owner and admins can read them).
CREATE TABLE customer_addresses (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    label          VARCHAR(40),
    recipient_name VARCHAR(150) NOT NULL,
    phone          VARCHAR(11)  NOT NULL,
    cep            VARCHAR(8)   NOT NULL,
    street         VARCHAR(150) NOT NULL,
    number         VARCHAR(20)  NOT NULL,
    complement     VARCHAR(100),
    district       VARCHAR(100) NOT NULL,
    city           VARCHAR(100) NOT NULL,
    state          VARCHAR(2)   NOT NULL,
    reference      VARCHAR(200),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_customer_addresses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_customer_addresses_user ON customer_addresses (user_id);

CREATE TABLE cart_items (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    product_id BIGINT      NOT NULL,
    quantity   INTEGER     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cart_items_user_product UNIQUE (user_id, product_id),
    CONSTRAINT ck_cart_items_quantity CHECK (quantity BETWEEN 1 AND 99),
    CONSTRAINT fk_cart_items_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);

-- Single-row store settings edited from the admin panel. A NULL delivery fee blocks checkout
-- until the owners set it.
CREATE TABLE store_settings (
    id           SMALLINT      PRIMARY KEY,
    delivery_fee NUMERIC(10, 2),
    updated_by   BIGINT,
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_store_settings_single_row CHECK (id = 1),
    CONSTRAINT ck_store_settings_fee CHECK (delivery_fee IS NULL OR delivery_fee >= 0),
    CONSTRAINT fk_store_settings_user FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
);

INSERT INTO store_settings (id) VALUES (1);

-- Orders keep a snapshot of prices, product names and the delivery address at checkout time,
-- so later catalog or address changes never alter a placed order. Users with orders cannot be
-- deleted (fiscal and consumer-law records).
CREATE TABLE orders (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    subtotal        NUMERIC(12, 2) NOT NULL,
    delivery_fee    NUMERIC(10, 2) NOT NULL,
    total           NUMERIC(12, 2) NOT NULL,
    recipient_name  VARCHAR(150)  NOT NULL,
    phone           VARCHAR(11)   NOT NULL,
    cep             VARCHAR(8)    NOT NULL,
    street          VARCHAR(150)  NOT NULL,
    number          VARCHAR(20)   NOT NULL,
    complement      VARCHAR(100),
    district        VARCHAR(100)  NOT NULL,
    city            VARCHAR(100)  NOT NULL,
    state           VARCHAR(2)    NOT NULL,
    reference       VARCHAR(200),
    idempotency_key VARCHAR(64)   NOT NULL,
    payment_due_at  TIMESTAMPTZ   NOT NULL,
    cancelled_at    TIMESTAMPTZ,
    cancel_reason   VARCHAR(200),
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_orders_user_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_orders_status CHECK (status IN
        ('AWAITING_PAYMENT', 'PAID', 'PREPARING', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT ck_orders_amounts CHECK (subtotal >= 0 AND delivery_fee >= 0 AND total = subtotal + delivery_fee),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_orders_user_created ON orders (user_id, created_at DESC);
CREATE INDEX idx_orders_status_due ON orders (status, payment_due_at);

CREATE TABLE order_items (
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT         NOT NULL,
    product_id   BIGINT,
    sku          VARCHAR(50)    NOT NULL,
    product_name VARCHAR(150)   NOT NULL,
    unit_price   NUMERIC(12, 2) NOT NULL,
    quantity     INTEGER        NOT NULL,
    line_total   NUMERIC(12, 2) NOT NULL,
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_items_total CHECK (line_total = unit_price * quantity),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE SET NULL
);

CREATE INDEX idx_order_items_order ON order_items (order_id);
