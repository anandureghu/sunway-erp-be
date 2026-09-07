-- Warehouse: type + capacity
ALTER TABLE warehouses
    ADD COLUMN warehouse_type VARCHAR(50) NULL,
    ADD COLUMN capacity DOUBLE NULL;

-- Vendor: supplier code + profile fields
ALTER TABLE vendor
    ADD COLUMN vendor_code VARCHAR(50) NULL,
    ADD COLUMN category_id BIGINT NULL,
    ADD COLUMN vendor_cr_no VARCHAR(100) NULL,
    ADD COLUMN bank_name VARCHAR(150) NULL,
    ADD COLUMN iban VARCHAR(64) NULL;

UPDATE vendor
SET vendor_code = CONCAT('SUP-', id)
WHERE vendor_code IS NULL OR vendor_code = '';

ALTER TABLE vendor
    MODIFY COLUMN vendor_code VARCHAR(50) NOT NULL;

CREATE UNIQUE INDEX uk_vendor_company_code ON vendor (company_id, vendor_code);

ALTER TABLE vendor
    ADD CONSTRAINT fk_vendor_category
        FOREIGN KEY (category_id) REFERENCES categories (id);
