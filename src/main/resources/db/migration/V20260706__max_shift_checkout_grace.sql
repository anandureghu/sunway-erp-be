-- Company HR policy: grace minutes after max shift (standard hours + OT cap)
-- before automatic attendance check-out. NULL/0 = check out at the cap with no grace.
-- Allowed UI values: 15, 20, or 30.
--
-- Idempotent: prod may already have this column from the original V20260705
-- migration (later renamed to V20260706 when exit-interview took that version),
-- or from a prior Flyway attempt where MySQL DDL committed before the history row.

DROP PROCEDURE IF EXISTS add_max_shift_checkout_grace;
DELIMITER //
CREATE PROCEDURE add_max_shift_checkout_grace()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'companies'
           AND COLUMN_NAME = 'max_shift_checkout_grace_minutes'
    ) THEN
        ALTER TABLE `companies`
            ADD COLUMN `max_shift_checkout_grace_minutes` INT NULL DEFAULT NULL;
    END IF;
END //
DELIMITER ;

CALL add_max_shift_checkout_grace();
DROP PROCEDURE add_max_shift_checkout_grace;
