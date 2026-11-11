CREATE TABLE categories (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(80) NOT NULL,
    slug       VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_categories_slug UNIQUE (slug)
);

-- product_type drives behaviour (e.g. the oil-change kit); categories are free navigation groups.
ALTER TABLE products
    ADD COLUMN product_type          VARCHAR(20)  NOT NULL DEFAULT 'OTHER',
    ADD COLUMN category_id           BIGINT,
    ADD COLUMN viscosity             VARCHAR(20),
    ADD COLUMN oil_specification     VARCHAR(100),
    ADD COLUMN package_volume_liters NUMERIC(5, 2),
    ADD CONSTRAINT ck_products_type CHECK (product_type IN
        ('ENGINE_OIL', 'OIL_FILTER', 'AIR_FILTER', 'FUEL_FILTER', 'CABIN_FILTER', 'OTHER')),
    ADD CONSTRAINT ck_products_volume CHECK (package_volume_liters IS NULL OR package_volume_liters > 0),
    ADD CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE SET NULL;

CREATE INDEX idx_products_category ON products (category_id);
CREATE INDEX idx_products_type ON products (product_type);

-- Ordered image URLs per product (files are hosted elsewhere; upload is a later decision).
CREATE TABLE product_images (
    product_id BIGINT       NOT NULL,
    position   INTEGER      NOT NULL,
    url        VARCHAR(500) NOT NULL,
    CONSTRAINT pk_product_images PRIMARY KEY (product_id, position),
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);
