CREATE TABLE vehicles (
    id                 BIGSERIAL PRIMARY KEY,
    plate              VARCHAR(8)   NOT NULL,
    brand              VARCHAR(100) NOT NULL,
    model              VARCHAR(150) NOT NULL,
    version            VARCHAR(150),
    manufacture_year   INTEGER      NOT NULL,
    model_year         INTEGER,
    color              VARCHAR(50),
    fuel               VARCHAR(50),
    vehicle_type       VARCHAR(50),
    species            VARCHAR(50),
    passenger_capacity INTEGER,
    engine_cc          INTEGER,
    origin             VARCHAR(50),
    source             VARCHAR(30)  NOT NULL DEFAULT 'SIMPLES_API',
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_vehicles_plate UNIQUE (plate)
);

CREATE INDEX idx_vehicles_brand_model ON vehicles (brand, model);
