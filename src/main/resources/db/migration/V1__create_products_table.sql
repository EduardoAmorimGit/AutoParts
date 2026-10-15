CREATE TABLE products (
    id             BIGSERIAL PRIMARY KEY,
    sku            VARCHAR(50)    NOT NULL,
    name           VARCHAR(150)   NOT NULL,
    description    TEXT,
    brand          VARCHAR(100),
    price          NUMERIC(12, 2) NOT NULL,
    stock_quantity INTEGER        NOT NULL DEFAULT 0,
    active         BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_products_sku UNIQUE (sku)
);

CREATE INDEX idx_products_name ON products (name);
