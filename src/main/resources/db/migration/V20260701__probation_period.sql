-- Probation workflow: companies get a configurable probation period (months);
-- new hires created while it is set start UNDER_PROBATION with a probation end
-- date, and must be confirmed once it ends.

DROP PROCEDURE IF EXISTS add_probation_columns;
DELIMITER //
CREATE PROCEDURE add_probation_columns()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'companies'
           AND COLUMN_NAME = 'probation_period_months'
    ) THEN
        ALTER TABLE companies
            ADD COLUMN probation_period_months INT NOT NULL DEFAULT 3;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'employees'
           AND COLUMN_NAME = 'probation_end_date'
    ) THEN
        ALTER TABLE employees ADD COLUMN probation_end_date DATE NULL;
    END IF;
END //
DELIMITER ;

CALL add_probation_columns();
DROP PROCEDURE add_probation_columns;
