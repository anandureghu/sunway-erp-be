-- picklist_number was globally unique across all companies, but the
-- generator (DocumentSequenceService) resets its counter per company.
-- Scope the uniqueness to the company.

DROP PROCEDURE IF EXISTS scope_picklist_number_uniqueness_to_company;
DELIMITER //
CREATE PROCEDURE scope_picklist_number_uniqueness_to_company()
BEGIN
    DECLARE old_idx VARCHAR(64);

    SELECT s.INDEX_NAME INTO old_idx
    FROM information_schema.STATISTICS s
    WHERE s.TABLE_SCHEMA = DATABASE()
      AND s.TABLE_NAME = 'picklists'
      AND s.NON_UNIQUE = 0
      AND s.INDEX_NAME <> 'PRIMARY'
    GROUP BY s.INDEX_NAME
    HAVING COUNT(*) = 1
       AND MAX(CASE WHEN s.COLUMN_NAME = 'picklist_number' THEN 1 ELSE 0 END) = 1
    LIMIT 1;

    IF old_idx IS NOT NULL THEN
        SET @sql = CONCAT('ALTER TABLE picklists DROP INDEX ', old_idx);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'picklists'
           AND INDEX_NAME = 'uk_picklists_company_picklist_number'
    ) THEN
        ALTER TABLE picklists
            ADD CONSTRAINT uk_picklists_company_picklist_number
            UNIQUE (company_id, picklist_number);
    END IF;
END //
DELIMITER ;

CALL scope_picklist_number_uniqueness_to_company();
DROP PROCEDURE scope_picklist_number_uniqueness_to_company;
