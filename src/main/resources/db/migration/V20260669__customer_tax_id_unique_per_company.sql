-- tax_id on customers was globally unique across all companies. Two
-- unrelated tenants' customers can legitimately share (or both leave blank)
-- a tax id. Scope the uniqueness to the company.

DROP PROCEDURE IF EXISTS scope_customer_tax_id_uniqueness_to_company;
DELIMITER //
CREATE PROCEDURE scope_customer_tax_id_uniqueness_to_company()
BEGIN
    DECLARE old_idx VARCHAR(64);

    SELECT s.INDEX_NAME INTO old_idx
    FROM information_schema.STATISTICS s
    WHERE s.TABLE_SCHEMA = DATABASE()
      AND s.TABLE_NAME = 'customers'
      AND s.NON_UNIQUE = 0
      AND s.INDEX_NAME <> 'PRIMARY'
    GROUP BY s.INDEX_NAME
    HAVING COUNT(*) = 1
       AND MAX(CASE WHEN s.COLUMN_NAME = 'tax_id' THEN 1 ELSE 0 END) = 1
    LIMIT 1;

    IF old_idx IS NOT NULL THEN
        SET @sql = CONCAT('ALTER TABLE customers DROP INDEX ', old_idx);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'customers'
           AND INDEX_NAME = 'uk_customers_company_tax_id'
    ) THEN
        ALTER TABLE customers
            ADD CONSTRAINT uk_customers_company_tax_id
            UNIQUE (company_id, tax_id);
    END IF;
END //
DELIMITER ;

CALL scope_customer_tax_id_uniqueness_to_company();
DROP PROCEDURE scope_customer_tax_id_uniqueness_to_company;
