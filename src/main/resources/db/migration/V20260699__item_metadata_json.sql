-- Store unmapped bulk-upload columns as JSON text on inventory items.

DROP PROCEDURE IF EXISTS add_items_metadata;
DELIMITER //
CREATE PROCEDURE add_items_metadata()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'items'
           AND COLUMN_NAME = 'metadata'
    ) THEN
        ALTER TABLE items
            ADD COLUMN metadata LONGTEXT NULL AFTER description;
    END IF;
END //
DELIMITER ;

CALL add_items_metadata();
DROP PROCEDURE add_items_metadata;
