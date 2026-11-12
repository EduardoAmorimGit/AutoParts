-- Delivery fee by region. Admins group the neighbourhoods (bairros) of the delivery area into
-- regions, each with its own fee. The customer's address district (filled from the CEP) picks
-- the region; a district in no active region, or in a region whose fee is not set yet (NULL),
-- is not served and checkout is refused.
CREATE TABLE delivery_regions (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(80)    NOT NULL,
    fee        NUMERIC(10, 2),
    active     BOOLEAN        NOT NULL DEFAULT TRUE,
    updated_by BIGINT,
    version    BIGINT         NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_delivery_regions_fee CHECK (fee IS NULL OR fee >= 0),
    CONSTRAINT fk_delivery_regions_user FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX uq_delivery_regions_name ON delivery_regions (LOWER(name));

-- district_key is the district name without accents, upper-case, single spaces; a district
-- belongs to at most one region.
CREATE TABLE delivery_region_districts (
    region_id    BIGINT       NOT NULL,
    name         VARCHAR(100) NOT NULL,
    district_key VARCHAR(100) NOT NULL,
    CONSTRAINT uq_delivery_region_districts_key UNIQUE (district_key),
    CONSTRAINT fk_delivery_region_districts_region FOREIGN KEY (region_id)
        REFERENCES delivery_regions (id) ON DELETE CASCADE
);

CREATE INDEX idx_delivery_region_districts_region ON delivery_region_districts (region_id);

-- Orders keep the name of the region used to price the delivery (NULL for older orders).
ALTER TABLE orders ADD COLUMN delivery_region VARCHAR(80);

-- The single fixed fee is replaced by the regions; store_settings held nothing else.
DROP TABLE store_settings;
