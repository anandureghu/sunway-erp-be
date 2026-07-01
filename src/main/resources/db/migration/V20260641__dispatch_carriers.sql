CREATE TABLE IF NOT EXISTS dispatch_carriers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    vehicle_number VARCHAR(100) DEFAULT NULL,
    driver_name VARCHAR(150) DEFAULT NULL,
    driver_phone VARCHAR(50) DEFAULT NULL,
    comments VARCHAR(1000) DEFAULT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) DEFAULT NULL,
    updated_at DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_dispatch_carriers_company (company_id),
    CONSTRAINT fk_dispatch_carriers_company FOREIGN KEY (company_id) REFERENCES companies (id)
);

ALTER TABLE shipments
    ADD COLUMN customer_phone VARCHAR(50) DEFAULT NULL;

ALTER TABLE shipments
    MODIFY COLUMN notes VARCHAR(1000) DEFAULT NULL;
