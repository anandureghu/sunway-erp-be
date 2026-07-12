-- tax_id on vendor was globally unique across all companies, even though
-- this table already scopes vendor_name correctly via
-- uk_... (company_id, vendor_name). Apply the same scoping to tax_id.

DROP PROCEDURE IF EXISTS scope_vendor_tax_id_uniqueness_to_company;
DELIMITER //
CREATE PROCEDURE scope_vendor_tax_id_uniqueness_to_company()
BEGIN
    DECLARE old_idx VARCHAR(64);

    SELECT s.INDEX_NAME INTO old_idx
    FROM information_schema.STATISTICS s
    WHERE s.TABLE_SCHEMA = DATABASE()
      AND s.TABLE_NAME = 'vendor'
      AND s.NON_UNIQUE = 0
      AND s.INDEX_NAME <> 'PRIMARY'
    GROUP BY s.INDEX_NAME
    HAVING COUNT(*) = 1
       AND MAX(CASE WHEN s.COLUMN_NAME = 'tax_id' THEN 1 ELSE 0 END) = 1
    LIMIT 1;

    IF old_idx IS NOT NULL THEN
        SET @sql = CONCAT('ALTER TABLE vendor DROP INDEX ', old_idx);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'vendor'
           AND INDEX_NAME = 'uk_vendor_company_tax_id'
    ) THEN
        ALTER TABLE vendor
            ADD CONSTRAINT uk_vendor_company_tax_id
            UNIQUE (company_id, tax_id);
    END IF;
END //
DELIMITER ;

CALL scope_vendor_tax_id_uniqueness_to_company();
DROP PROCEDURE scope_vendor_tax_id_uniqueness_to_company;
