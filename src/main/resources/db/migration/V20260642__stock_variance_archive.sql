-- Stock variance archive flag (idempotent for environments where the column
-- was added manually before this migration was recorded in Flyway).

DROP PROCEDURE IF EXISTS add_stock_variance_archive;
DELIMITER //
CREATE PROCEDURE add_stock_variance_archive()
BEGIN
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

CALL add_stock_variance_archive();
DROP PROCEDURE add_stock_variance_archive;
