-- Repair Azure/production schema drift where objects exist before Flyway
-- records the migration. Idempotent: safe on every environment.

DROP PROCEDURE IF EXISTS repair_production_schema_drift;
DELIMITER //
CREATE PROCEDURE repair_production_schema_drift()
BEGIN
    -- users.two_factor_enabled (V20260637)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'users'
           AND COLUMN_NAME = 'two_factor_enabled'
    ) THEN
        ALTER TABLE users
            ADD COLUMN two_factor_enabled bit(1) NOT NULL DEFAULT b'0' AFTER force_password_reset;
    END IF;

    -- purchase_requisitions.notes (V20260638)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'purchase_requisitions'
           AND COLUMN_NAME = 'notes'
    ) THEN
        ALTER TABLE purchase_requisitions
            ADD COLUMN notes VARCHAR(2000) NULL;
    END IF;

    -- employees.middle_name (V20260639)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'employees'
           AND COLUMN_NAME = 'middle_name'
    ) THEN
        ALTER TABLE employees
            ADD COLUMN middle_name VARCHAR(50) NULL AFTER first_name;
    END IF;

    -- goods_receipt_items FIFO columns (V20260640)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'goods_receipt_items'
           AND COLUMN_NAME = 'batch_no'
    ) THEN
        ALTER TABLE goods_receipt_items
            ADD COLUMN batch_no varchar(100) DEFAULT NULL,
            ADD COLUMN lot_no varchar(100) DEFAULT NULL,
            ADD COLUMN unit_cost decimal(18,2) DEFAULT NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'sales_order_items'
           AND COLUMN_NAME = 'cogs_amount'
    ) THEN
        ALTER TABLE sales_order_items
            ADD COLUMN cogs_amount decimal(18,2) DEFAULT NULL,
            ADD COLUMN fifo_unit_cost decimal(18,2) DEFAULT NULL;
    END IF;

    -- dispatch carriers + shipment phone (V20260641)
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

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'shipments'
           AND COLUMN_NAME = 'customer_phone'
    ) THEN
        ALTER TABLE shipments
            ADD COLUMN customer_phone VARCHAR(50) DEFAULT NULL;
    END IF;

    -- stock_variances.archived (V20260642)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'stock_variances'
           AND COLUMN_NAME = 'archived'
    ) THEN
        ALTER TABLE stock_variances
            ADD COLUMN archived tinyint(1) NOT NULL DEFAULT 0 AFTER rejected_at;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'stock_variances'
           AND INDEX_NAME = 'idx_stock_variances_company_status_archived'
    ) THEN
        CREATE INDEX idx_stock_variances_company_status_archived
            ON stock_variances (company_id, variance_status, archived);
    END IF;
END //
DELIMITER ;

CALL repair_production_schema_drift();
DROP PROCEDURE repair_production_schema_drift;
