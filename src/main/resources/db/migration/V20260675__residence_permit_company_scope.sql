-- employee_residence_permits.permit_id_number was globally unique, but the
-- table only reaches a company transitively through employee_id. Add a
-- direct company_id (backfilled from the employee), then scope
-- permit_id_number uniqueness to the company.

DROP PROCEDURE IF EXISTS migrate_residence_permits_company_scope;
DELIMITER //
CREATE PROCEDURE migrate_residence_permits_company_scope()
BEGIN
    DECLARE old_idx VARCHAR(64);

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'employee_residence_permits'
           AND COLUMN_NAME = 'company_id'
    ) THEN
        ALTER TABLE employee_residence_permits ADD COLUMN company_id BIGINT NULL;
    END IF;

    UPDATE employee_residence_permits p
       JOIN employees e ON p.employee_id = e.id
       SET p.company_id = e.company_id
     WHERE p.company_id IS NULL;

    ALTER TABLE employee_residence_permits MODIFY COLUMN company_id BIGINT NOT NULL;

    SELECT s.INDEX_NAME INTO old_idx
    FROM information_schema.STATISTICS s
    WHERE s.TABLE_SCHEMA = DATABASE()
      AND s.TABLE_NAME = 'employee_residence_permits'
      AND s.NON_UNIQUE = 0
      AND s.INDEX_NAME <> 'PRIMARY'
    GROUP BY s.INDEX_NAME
    HAVING COUNT(*) = 1
       AND MAX(CASE WHEN s.COLUMN_NAME = 'permit_id_number' THEN 1 ELSE 0 END) = 1
    LIMIT 1;

    IF old_idx IS NOT NULL THEN
        SET @sql = CONCAT('ALTER TABLE employee_residence_permits DROP INDEX ', old_idx);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'employee_residence_permits'
           AND INDEX_NAME = 'uk_residence_permits_company_permit_id_number'
    ) THEN
        ALTER TABLE employee_residence_permits
            ADD CONSTRAINT uk_residence_permits_company_permit_id_number
            UNIQUE (company_id, permit_id_number);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.KEY_COLUMN_USAGE
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'employee_residence_permits'
           AND COLUMN_NAME = 'company_id'
           AND REFERENCED_TABLE_NAME = 'companies'
    ) THEN
        ALTER TABLE employee_residence_permits
            ADD CONSTRAINT fk_residence_permits_company
            FOREIGN KEY (company_id) REFERENCES companies(id);
    END IF;
END //
DELIMITER ;

CALL migrate_residence_permits_company_scope();
DROP PROCEDURE migrate_residence_permits_company_scope;
