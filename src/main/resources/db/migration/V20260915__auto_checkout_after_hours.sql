-- Company HR policy: auto check-out after a fixed on-clock duration.
-- Allowed values: 8, 10, or 12 hours. Replaces grace-minute options (0/15/20/30).
--
-- Idempotent for environments that may already have the column.

DROP PROCEDURE IF EXISTS add_auto_checkout_after_hours;
DELIMITER //
CREATE PROCEDURE add_auto_checkout_after_hours()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'companies'
           AND COLUMN_NAME = 'auto_checkout_after_hours'
    ) THEN
        ALTER TABLE `companies`
            ADD COLUMN `auto_checkout_after_hours` INT NULL DEFAULT 10;
    END IF;
END //
DELIMITER ;

CALL add_auto_checkout_after_hours();
DROP PROCEDURE add_auto_checkout_after_hours;

UPDATE `companies`
   SET `auto_checkout_after_hours` = 10
 WHERE `auto_checkout_after_hours` IS NULL
    OR `auto_checkout_after_hours` NOT IN (8, 10, 12);
