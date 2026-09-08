-- Extra catalog fields from item master spreadsheets
ALTER TABLE items
    ADD COLUMN criticality VARCHAR(32) NULL,
    ADD COLUMN hsn_code VARCHAR(64) NULL,
    ADD COLUMN vat_applicable TINYINT(1) NULL,
    ADD COLUMN reorder_qty INT NULL,
    ADD COLUMN lead_time_days INT NULL,
    ADD COLUMN preferred_vendor_id BIGINT NULL,
    ADD COLUMN supplier_part_no VARCHAR(150) NULL,
    ADD COLUMN weight_kg DOUBLE NULL,
    ADD COLUMN dimensions VARCHAR(100) NULL,
    ADD COLUMN warranty_months INT NULL,
    ADD COLUMN remarks TEXT NULL;

ALTER TABLE items
    ADD CONSTRAINT fk_items_preferred_vendor
        FOREIGN KEY (preferred_vendor_id) REFERENCES vendor (id);
