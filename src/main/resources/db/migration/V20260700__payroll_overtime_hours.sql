-- Payroll now records overtime hours (worked hours beyond the standard for the
-- pay period) so the payroll processing screen can surface total overtime.

DROP PROCEDURE IF EXISTS add_payroll_overtime_hours;
DELIMITER //
CREATE PROCEDURE add_payroll_overtime_hours()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'payroll'
           AND COLUMN_NAME = 'overtime_hours'
    ) THEN
        ALTER TABLE payroll ADD COLUMN overtime_hours DOUBLE NOT NULL DEFAULT 0;
    END IF;
END //
DELIMITER ;

CALL add_payroll_overtime_hours();
DROP PROCEDURE add_payroll_overtime_hours;
