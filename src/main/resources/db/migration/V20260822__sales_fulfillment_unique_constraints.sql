-- Harden sales fulfillment uniqueness (one picklist per SO, one shipment per picklist).
-- Skip when duplicates already exist so Flyway does not fail on dirty data.

SET @db := DATABASE();

SET @dup_picklists := (
  SELECT COUNT(*) FROM (
    SELECT sales_order_id
    FROM picklists
    WHERE sales_order_id IS NOT NULL
    GROUP BY sales_order_id
    HAVING COUNT(*) > 1
  ) d
);

SET @sql := (
  SELECT IF(
    @dup_picklists > 0
      OR EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = @db
          AND table_name = 'picklists'
          AND index_name = 'uk_picklists_sales_order_id'
      ),
    'SELECT 1',
    'ALTER TABLE picklists ADD UNIQUE INDEX uk_picklists_sales_order_id (sales_order_id)'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @dup_shipments := (
  SELECT COUNT(*) FROM (
    SELECT picklist_id
    FROM shipments
    WHERE picklist_id IS NOT NULL
    GROUP BY picklist_id
    HAVING COUNT(*) > 1
  ) d
);

SET @sql := (
  SELECT IF(
    @dup_shipments > 0
      OR EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = @db
          AND table_name = 'shipments'
          AND index_name = 'uk_shipments_picklist_id'
      ),
    'SELECT 1',
    'ALTER TABLE shipments ADD UNIQUE INDEX uk_shipments_picklist_id (picklist_id)'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
