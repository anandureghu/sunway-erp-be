-- Soft-archive flag for stock batch movements (idempotent).

DROP PROCEDURE IF EXISTS add_stock_batch_movement_archive;
DELIMITER //
CREATE PROCEDURE add_stock_batch_movement_archive()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'stock_batch_movements'
           AND COLUMN_NAME = 'archived'
    ) THEN
        ALTER TABLE stock_batch_movements
            ADD COLUMN archived tinyint(1) NOT NULL DEFAULT 0 AFTER created_at;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'stock_batch_movements'
           AND INDEX_NAME = 'idx_stock_batch_movements_archived'
    ) THEN
        CREATE INDEX idx_stock_batch_movements_archived
            ON stock_batch_movements (archived);
    END IF;
END //
DELIMITER ;

CALL add_stock_batch_movement_archive();
DROP PROCEDURE add_stock_batch_movement_archive;
