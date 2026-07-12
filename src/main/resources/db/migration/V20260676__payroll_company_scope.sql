-- payroll.payroll_code was annotated unique=true on the entity but no live
-- unique index ever existed in the database (dead annotation). Add a direct
-- company_id (backfilled from the employee) and a real per-company unique
-- constraint before another change relies on the annotation being accurate.

DROP PROCEDURE IF EXISTS migrate_payroll_company_scope;
DELIMITER //
CREATE PROCEDURE migrate_payroll_company_scope()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'payroll'
           AND COLUMN_NAME = 'company_id'
    ) THEN
        ALTER TABLE payroll ADD COLUMN company_id BIGINT NULL;
    END IF;

    UPDATE payroll p
       JOIN employees e ON p.employee_id = e.id
       SET p.company_id = e.company_id
     WHERE p.company_id IS NULL;

    ALTER TABLE payroll MODIFY COLUMN company_id BIGINT NOT NULL;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'payroll'
           AND INDEX_NAME = 'uk_payroll_company_payroll_code'
    ) THEN
        ALTER TABLE payroll
            ADD CONSTRAINT uk_payroll_company_payroll_code
            UNIQUE (company_id, payroll_code);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.KEY_COLUMN_USAGE
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'payroll'
           AND COLUMN_NAME = 'company_id'
           AND REFERENCED_TABLE_NAME = 'companies'
    ) THEN
        ALTER TABLE payroll
            ADD CONSTRAINT fk_payroll_company
            FOREIGN KEY (company_id) REFERENCES companies(id);
    END IF;
END //
DELIMITER ;

CALL migrate_payroll_company_scope();
DROP PROCEDURE migrate_payroll_company_scope;
