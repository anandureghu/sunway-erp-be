-- Repair: V20260688 is recorded in flyway_schema_history on some environments
-- (checksum repaired on startup) but its columns were never added to companies /
-- employee_timesheets. Idempotent — safe when the columns already exist.

DROP PROCEDURE IF EXISTS repair_attendance_working_hours_and_checkin;
DELIMITER //
CREATE PROCEDURE repair_attendance_working_hours_and_checkin()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'companies'
           AND COLUMN_NAME = 'standard_working_hours_per_day'
    ) THEN
        ALTER TABLE `companies`
            ADD COLUMN `standard_working_hours_per_day` DECIMAL(4,2) NOT NULL DEFAULT 6.00;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'companies'
           AND COLUMN_NAME = 'require_check_in'
    ) THEN
        ALTER TABLE `companies`
            ADD COLUMN `require_check_in` TINYINT(1) NOT NULL DEFAULT 1;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'employee_timesheets'
           AND COLUMN_NAME = 'auto_checked_out'
    ) THEN
        ALTER TABLE `employee_timesheets`
            ADD COLUMN `auto_checked_out` TINYINT(1) NOT NULL DEFAULT 0;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'employee_timesheets'
           AND COLUMN_NAME = 'note'
    ) THEN
        ALTER TABLE `employee_timesheets`
            ADD COLUMN `note` VARCHAR(255) NULL;
    END IF;
END //
DELIMITER ;

CALL repair_attendance_working_hours_and_checkin();
DROP PROCEDURE repair_attendance_working_hours_and_checkin;
