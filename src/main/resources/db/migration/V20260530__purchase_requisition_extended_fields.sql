ALTER TABLE purchase_requisitions
    ADD COLUMN requested_date DATE NULL,
    ADD COLUMN required_delivery_date DATE NULL,
    ADD COLUMN project_code VARCHAR(64) NULL,
    ADD COLUMN requisition_description VARCHAR(2000) NULL,
    ADD COLUMN urgency VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN required_by_date DATE NULL,
    ADD COLUMN delivery_warehouse_id BIGINT NULL,
    ADD COLUMN justification VARCHAR(2000) NULL,
    ADD CONSTRAINT FK_pr_delivery_warehouse
        FOREIGN KEY (delivery_warehouse_id) REFERENCES warehouses (id);
