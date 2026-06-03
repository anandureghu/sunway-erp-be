-- Contract IDs now use native MySQL AUTO_INCREMENT (GenerationType.IDENTITY),
-- consistent with every other entity. This removes the dependency on the
-- emulated `contract_sequence` table, which under ddl-auto=validate was never
-- seeded and failed every insert with "could not read a hi value".
--
-- contracts.id is referenced by a foreign key from salary_allowances, and MySQL
-- won't let you add AUTO_INCREMENT to a column under an FK — so drop the FK,
-- alter the column, then re-create the FK. The FK name is discovered at runtime
-- so this works regardless of the (Hibernate-generated) constraint name.

SET @fk := (
    SELECT CONSTRAINT_NAME
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'salary_allowances'
      AND REFERENCED_TABLE_NAME = 'contracts'
    LIMIT 1
);
SET @sql := IF(@fk IS NULL, 'SELECT 1',
    CONCAT('ALTER TABLE salary_allowances DROP FOREIGN KEY `', @fk, '`'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE contracts MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE salary_allowances
    ADD CONSTRAINT fk_salary_allowances_contract
    FOREIGN KEY (contract_id) REFERENCES contracts (id);

-- No longer used by any entity.
DROP TABLE IF EXISTS contract_sequence;
