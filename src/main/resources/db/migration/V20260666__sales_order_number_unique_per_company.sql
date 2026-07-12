-- order_number on sales_orders was globally unique across all companies, but
-- the generator (DocumentSequenceService) resets its counter per company.
-- Scope the uniqueness to the company.

DROP PROCEDURE IF EXISTS scope_sales_order_number_uniqueness_to_company;
DELIMITER //
CREATE PROCEDURE scope_sales_order_number_uniqueness_to_company()
BEGIN
    DECLARE old_idx VARCHAR(64);

    SELECT s.INDEX_NAME INTO old_idx
    FROM information_schema.STATISTICS s
    WHERE s.TABLE_SCHEMA = DATABASE()
      AND s.TABLE_NAME = 'sales_orders'
      AND s.NON_UNIQUE = 0
      AND s.INDEX_NAME <> 'PRIMARY'
    GROUP BY s.INDEX_NAME
    HAVING COUNT(*) = 1
       AND MAX(CASE WHEN s.COLUMN_NAME = 'order_number' THEN 1 ELSE 0 END) = 1
    LIMIT 1;

    IF old_idx IS NOT NULL THEN
        SET @sql = CONCAT('ALTER TABLE sales_orders DROP INDEX ', old_idx);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'sales_orders'
           AND INDEX_NAME = 'uk_sales_orders_company_order_number'
    ) THEN
        ALTER TABLE sales_orders
            ADD CONSTRAINT uk_sales_orders_company_order_number
            UNIQUE (company_id, order_number);
    END IF;
END //
DELIMITER ;

CALL scope_sales_order_number_uniqueness_to_company();
DROP PROCEDURE scope_sales_order_number_uniqueness_to_company;
