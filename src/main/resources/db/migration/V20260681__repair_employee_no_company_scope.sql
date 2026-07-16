-- V20260648__employee_no_per_company.sql was recorded as successful in
-- flyway_schema_history, but on at least one environment its DROP INDEX /
-- ADD CONSTRAINT never actually took effect: employees.employee_no is still
-- covered by V1's hardcoded global UNIQUE KEY `UK62tjkkmdtpl2vmweafp9rensh`,
-- and the intended `uk_employee_company_no` composite was never created.
-- Re-apply the fix here using the stored-procedure pattern (proven reliable
-- elsewhere in this migration series) instead of bare SET/PREPARE statements,
-- so it is safe to re-run and doesn't silently no-op.

DROP PROCEDURE IF EXISTS repair_employee_no_company_scope;
DELIMITER //
CREATE PROCEDURE repair_employee_no_company_scope()
BEGIN
    DECLARE old_idx VARCHAR(64);

    SELECT s.INDEX_NAME INTO old_idx
    FROM information_schema.STATISTICS s
    WHERE s.TABLE_SCHEMA = DATABASE()
      AND s.TABLE_NAME = 'employees'
      AND s.NON_UNIQUE = 0
      AND s.INDEX_NAME <> 'PRIMARY'
    GROUP BY s.INDEX_NAME
    HAVING COUNT(*) = 1
       AND MAX(CASE WHEN s.COLUMN_NAME = 'employee_no' THEN 1 ELSE 0 END) = 1
    LIMIT 1;

    IF old_idx IS NOT NULL THEN
        SET @sql = CONCAT('ALTER TABLE employees DROP INDEX ', old_idx);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'employees'
           AND INDEX_NAME = 'uk_employee_company_no'
    ) THEN
        ALTER TABLE employees
            ADD CONSTRAINT uk_employee_company_no
            UNIQUE (company_id, employee_no);
    END IF;
END //
DELIMITER ;

CALL repair_employee_no_company_scope();
DROP PROCEDURE repair_employee_no_company_scope;
