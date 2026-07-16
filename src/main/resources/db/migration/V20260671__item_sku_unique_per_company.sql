-- sku on items was globally unique across all companies. Two different
-- tenants both using SKU "ITM-001" collide. Scope the uniqueness to the
-- company.

DROP PROCEDURE IF EXISTS scope_item_sku_uniqueness_to_company;
DELIMITER //
CREATE PROCEDURE scope_item_sku_uniqueness_to_company()
BEGIN
    DECLARE old_idx VARCHAR(64);

    SELECT s.INDEX_NAME INTO old_idx
    FROM information_schema.STATISTICS s
    WHERE s.TABLE_SCHEMA = DATABASE()
      AND s.TABLE_NAME = 'items'
      AND s.NON_UNIQUE = 0
      AND s.INDEX_NAME <> 'PRIMARY'
    GROUP BY s.INDEX_NAME
    HAVING COUNT(*) = 1
       AND MAX(CASE WHEN s.COLUMN_NAME = 'sku' THEN 1 ELSE 0 END) = 1
    LIMIT 1;

    IF old_idx IS NOT NULL THEN
        SET @sql = CONCAT('ALTER TABLE items DROP INDEX ', old_idx);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'items'
           AND INDEX_NAME = 'uk_items_company_sku'
    ) THEN
        ALTER TABLE items
            ADD CONSTRAINT uk_items_company_sku
            UNIQUE (company_id, sku);
    END IF;
END //
DELIMITER ;

CALL scope_item_sku_uniqueness_to_company();
DROP PROCEDURE scope_item_sku_uniqueness_to_company;
