CREATE TABLE product_vehicle (
    id         BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    vehicle_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_product_vehicle_product FOREIGN KEY (product_id)
        REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_vehicle_vehicle FOREIGN KEY (vehicle_id)
        REFERENCES vehicles (id) ON DELETE CASCADE,
    CONSTRAINT uq_product_vehicle UNIQUE (product_id, vehicle_id)
);

CREATE INDEX idx_product_vehicle_vehicle ON product_vehicle (vehicle_id);
