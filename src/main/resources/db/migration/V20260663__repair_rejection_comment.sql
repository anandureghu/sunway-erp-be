-- Repair: V20260650 is recorded in flyway_schema_history on some environments
-- but rejection_comment was never added to employee_leaves / employee_loans.
-- Idempotent — safe when the columns already exist.

DROP PROCEDURE IF EXISTS repair_rejection_comment;
DELIMITER //
CREATE PROCEDURE repair_rejection_comment()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'employee_leaves'
           AND COLUMN_NAME = 'rejection_comment'
    ) THEN
        ALTER TABLE `employee_leaves` ADD COLUMN `rejection_comment` VARCHAR(1000) NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'employee_loans'
           AND COLUMN_NAME = 'rejection_comment'
    ) THEN
        ALTER TABLE `employee_loans` ADD COLUMN `rejection_comment` VARCHAR(1000) NULL;
    END IF;
END //
DELIMITER ;

CALL repair_rejection_comment();
DROP PROCEDURE repair_rejection_comment;
