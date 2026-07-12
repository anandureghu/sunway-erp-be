-- employee_loans.loan_code was globally unique, but the table only reaches a
-- company transitively through employee_id. Add a direct company_id
-- (backfilled from the employee), then scope loan_code uniqueness to the
-- company.

DROP PROCEDURE IF EXISTS migrate_employee_loans_company_scope;
DELIMITER //
CREATE PROCEDURE migrate_employee_loans_company_scope()
BEGIN
    DECLARE old_idx VARCHAR(64);

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'employee_loans'
           AND COLUMN_NAME = 'company_id'
    ) THEN
        ALTER TABLE employee_loans ADD COLUMN company_id BIGINT NULL;
    END IF;

    UPDATE employee_loans el
       JOIN employees e ON el.employee_id = e.id
       SET el.company_id = e.company_id
     WHERE el.company_id IS NULL;

    ALTER TABLE employee_loans MODIFY COLUMN company_id BIGINT NOT NULL;

    SELECT s.INDEX_NAME INTO old_idx
    FROM information_schema.STATISTICS s
    WHERE s.TABLE_SCHEMA = DATABASE()
      AND s.TABLE_NAME = 'employee_loans'
      AND s.NON_UNIQUE = 0
      AND s.INDEX_NAME <> 'PRIMARY'
    GROUP BY s.INDEX_NAME
    HAVING COUNT(*) = 1
       AND MAX(CASE WHEN s.COLUMN_NAME = 'loan_code' THEN 1 ELSE 0 END) = 1
    LIMIT 1;

    IF old_idx IS NOT NULL THEN
        SET @sql = CONCAT('ALTER TABLE employee_loans DROP INDEX ', old_idx);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'employee_loans'
           AND INDEX_NAME = 'uk_employee_loans_company_loan_code'
    ) THEN
        ALTER TABLE employee_loans
            ADD CONSTRAINT uk_employee_loans_company_loan_code
            UNIQUE (company_id, loan_code);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.KEY_COLUMN_USAGE
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'employee_loans'
           AND COLUMN_NAME = 'company_id'
           AND REFERENCED_TABLE_NAME = 'companies'
    ) THEN
        ALTER TABLE employee_loans
            ADD CONSTRAINT fk_employee_loans_company
            FOREIGN KEY (company_id) REFERENCES companies(id);
    END IF;
END //
DELIMITER ;

CALL migrate_employee_loans_company_scope();
DROP PROCEDURE migrate_employee_loans_company_scope;
