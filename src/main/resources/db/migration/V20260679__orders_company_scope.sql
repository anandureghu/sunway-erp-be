-- orders had no company relation at all, not even transitively — only a
-- nullable `supplier` FK to vendor, which itself carries company_id. Add a
-- direct company_id (backfilled from the linked vendor), then scope
-- order_id uniqueness to the company. Rows with no supplier (and therefore
-- no way to backfill a company) are deleted rather than left orphaned —
-- Orders has no other tenant signal to fall back on.

DROP PROCEDURE IF EXISTS migrate_orders_company_scope;
DELIMITER //
CREATE PROCEDURE migrate_orders_company_scope()
BEGIN
    DECLARE old_idx VARCHAR(64);

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'orders'
           AND COLUMN_NAME = 'company_id'
    ) THEN
        ALTER TABLE orders ADD COLUMN company_id BIGINT NULL;
    END IF;

    UPDATE orders o
       JOIN vendor v ON o.supplier = v.id
       SET o.company_id = v.company_id
     WHERE o.company_id IS NULL;

    DELETE FROM orders WHERE company_id IS NULL;

    ALTER TABLE orders MODIFY COLUMN company_id BIGINT NOT NULL;

    SELECT s.INDEX_NAME INTO old_idx
    FROM information_schema.STATISTICS s
    WHERE s.TABLE_SCHEMA = DATABASE()
      AND s.TABLE_NAME = 'orders'
      AND s.NON_UNIQUE = 0
      AND s.INDEX_NAME <> 'PRIMARY'
    GROUP BY s.INDEX_NAME
    HAVING COUNT(*) = 1
       AND MAX(CASE WHEN s.COLUMN_NAME = 'order_id' THEN 1 ELSE 0 END) = 1
    LIMIT 1;

    IF old_idx IS NOT NULL THEN
        SET @sql = CONCAT('ALTER TABLE orders DROP INDEX ', old_idx);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'orders'
           AND INDEX_NAME = 'uk_orders_company_order_id'
    ) THEN
        ALTER TABLE orders
            ADD CONSTRAINT uk_orders_company_order_id
            UNIQUE (company_id, order_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.KEY_COLUMN_USAGE
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'orders'
           AND COLUMN_NAME = 'company_id'
           AND REFERENCED_TABLE_NAME = 'companies'
    ) THEN
        ALTER TABLE orders
            ADD CONSTRAINT fk_orders_company
            FOREIGN KEY (company_id) REFERENCES companies(id);
    END IF;
END //
DELIMITER ;

CALL migrate_orders_company_scope();
DROP PROCEDURE migrate_orders_company_scope;
