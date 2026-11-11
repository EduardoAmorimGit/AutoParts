-- A vehicle spec ("ficha") is a model as catalogs describe it: brand, model, optional version and
-- engine, fuel and a model-year range. Text columns hold normalized values (uppercase, no accents).
CREATE TABLE vehicle_specs (
    id                  BIGSERIAL PRIMARY KEY,
    brand               VARCHAR(60)   NOT NULL,
    model               VARCHAR(100)  NOT NULL,
    version             VARCHAR(100),
    engine              VARCHAR(60),
    fuel                VARCHAR(20),
    year_start          INTEGER       NOT NULL,
    year_end            INTEGER       NOT NULL,
    oil_capacity_liters NUMERIC(4, 2),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_vehicle_specs_years CHECK (year_start >= 1950 AND year_end <= 2100 AND year_start <= year_end),
    CONSTRAINT ck_vehicle_specs_oil CHECK (oil_capacity_liters IS NULL OR oil_capacity_liters > 0)
);

CREATE UNIQUE INDEX uq_vehicle_specs_identity ON vehicle_specs
    (brand, model, COALESCE(version, ''), COALESCE(engine, ''), COALESCE(fuel, ''), year_start, year_end);
CREATE INDEX idx_vehicle_specs_brand_years ON vehicle_specs (brand, year_start, year_end);

-- A fitment says a product fits a spec. Only VALIDATED fitments are shown to customers, and every
-- fitment records where the information came from (catalog, supplier sheet, manual check).
CREATE TABLE product_fitments (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT       NOT NULL,
    spec_id     BIGINT       NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    source      VARCHAR(300) NOT NULL,
    notes       VARCHAR(500),
    reviewed_by BIGINT,
    reviewed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_product_fitments UNIQUE (product_id, spec_id),
    CONSTRAINT ck_product_fitments_status CHECK (status IN ('IMPORTED', 'VALIDATED', 'REJECTED')),
    CONSTRAINT fk_product_fitments_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_fitments_spec FOREIGN KEY (spec_id) REFERENCES vehicle_specs (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_fitments_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_product_fitments_spec_status ON product_fitments (spec_id, status);
CREATE INDEX idx_product_fitments_status ON product_fitments (status);

-- Which spec a looked-up vehicle (plate) resolved to. AUTO = matching rules, MANUAL = set by an admin.
CREATE TABLE vehicle_spec_assignments (
    vehicle_id  BIGINT      PRIMARY KEY,
    spec_id     BIGINT      NOT NULL,
    match_type  VARCHAR(10) NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_vehicle_spec_assignments_type CHECK (match_type IN ('AUTO', 'MANUAL')),
    CONSTRAINT fk_vehicle_spec_assignments_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id) ON DELETE CASCADE,
    CONSTRAINT fk_vehicle_spec_assignments_spec FOREIGN KEY (spec_id) REFERENCES vehicle_specs (id) ON DELETE CASCADE
);

CREATE INDEX idx_vehicle_spec_assignments_spec ON vehicle_spec_assignments (spec_id);

-- When an admin resolves a vehicle by hand, the provider data signature is remembered so every
-- later plate with identical data resolves to the same spec without asking again.
CREATE TABLE vehicle_spec_aliases (
    id         BIGSERIAL PRIMARY KEY,
    signature  VARCHAR(400) NOT NULL,
    spec_id    BIGINT       NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_vehicle_spec_aliases_signature UNIQUE (signature),
    CONSTRAINT fk_vehicle_spec_aliases_spec FOREIGN KEY (spec_id) REFERENCES vehicle_specs (id) ON DELETE CASCADE,
    CONSTRAINT fk_vehicle_spec_aliases_user FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
);
