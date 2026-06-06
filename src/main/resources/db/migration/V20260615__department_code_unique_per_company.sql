-- Department codes must be unique per company, not globally.
-- The legacy UK on department_code alone blocks the same code across tenants.

DROP PROCEDURE IF EXISTS fix_department_code_unique_per_company;
DELIMITER //
CREATE PROCEDURE fix_department_code_unique_per_company()
BEGIN
    IF EXISTS (
        SELECT 1
          FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME   = 'departments'
           AND INDEX_NAME   = 'UK89g8qie2y696a3tarmty43sq9'
    ) THEN
        ALTER TABLE departments DROP INDEX UK89g8qie2y696a3tarmty43sq9;
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME   = 'departments'
           AND INDEX_NAME   = 'uk_departments_company_code'
    ) THEN
        ALTER TABLE departments
            ADD CONSTRAINT uk_departments_company_code
                UNIQUE (company_id, department_code);
    END IF;
END //
DELIMITER ;

CALL fix_department_code_unique_per_company();
DROP PROCEDURE fix_department_code_unique_per_company;
