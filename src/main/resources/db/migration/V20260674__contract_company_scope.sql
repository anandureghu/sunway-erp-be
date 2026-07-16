-- contracts.contract_code was globally unique, but the table only reaches a
-- company transitively through employee_id, so no composite constraint was
-- possible. Add a direct company_id (backfilled from the employee), then
-- scope contract_code uniqueness to the company.

DROP PROCEDURE IF EXISTS migrate_contracts_company_scope;
DELIMITER //
CREATE PROCEDURE migrate_contracts_company_scope()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'contracts'
           AND COLUMN_NAME = 'company_id'
    ) THEN
        ALTER TABLE contracts ADD COLUMN company_id BIGINT NULL;
    END IF;

    UPDATE contracts c
       JOIN employees e ON c.employee_id = e.id
       SET c.company_id = e.company_id
     WHERE c.company_id IS NULL;

    ALTER TABLE contracts MODIFY COLUMN company_id BIGINT NOT NULL;

    IF EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'contracts'
           AND INDEX_NAME = 'uk_contract_code'
    ) THEN
        ALTER TABLE contracts DROP INDEX uk_contract_code;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'contracts'
           AND INDEX_NAME = 'uk_contracts_company_contract_code'
    ) THEN
        ALTER TABLE contracts
            ADD CONSTRAINT uk_contracts_company_contract_code
            UNIQUE (company_id, contract_code);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.KEY_COLUMN_USAGE
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'contracts'
           AND COLUMN_NAME = 'company_id'
           AND REFERENCED_TABLE_NAME = 'companies'
    ) THEN
        ALTER TABLE contracts
            ADD CONSTRAINT fk_contracts_company
            FOREIGN KEY (company_id) REFERENCES companies(id);
    END IF;
END //
DELIMITER ;

CALL migrate_contracts_company_scope();
DROP PROCEDURE migrate_contracts_company_scope;
