-- Archive support for picklists, matching the goods_receipts/stock_variances convention.

DROP PROCEDURE IF EXISTS add_picklist_archive;
DELIMITER //
CREATE PROCEDURE add_picklist_archive()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'picklists'
           AND COLUMN_NAME = 'archived'
    ) THEN
        ALTER TABLE picklists
            ADD COLUMN archived tinyint(1) NOT NULL DEFAULT 0 AFTER status;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'picklists'
           AND INDEX_NAME = 'idx_picklists_company_status_archived'
    ) THEN
        CREATE INDEX idx_picklists_company_status_archived
            ON picklists (company_id, status, archived);
    END IF;
END //
DELIMITER ;

CALL add_picklist_archive();
DROP PROCEDURE add_picklist_archive;
