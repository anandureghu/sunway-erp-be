-- =============================================================================
-- Re-aim the Division relationship: a Department belongs to a Division
-- (many departments per division). The legacy Division entity carried a
-- department_id (Division -> Department) which is the inverse direction; that
-- column is no longer referenced by the entity and is dropped here.
--
-- After this migration:
--   * departments.division_id  -> divisions(id)  (nullable)
--   * division.department_id   removed
-- =============================================================================

-- 1. Add division_id on departments.
ALTER TABLE departments
    ADD COLUMN division_id BIGINT NULL;

ALTER TABLE departments
    ADD CONSTRAINT fk_department_division
        FOREIGN KEY (division_id) REFERENCES division(id);

-- 2. Drop the inverse FK column on division if present.
--    Wrapped in a stored procedure so the migration is idempotent for
--    environments where Hibernate ddl-auto never created the column.
DROP PROCEDURE IF EXISTS drop_division_department_fk;
DELIMITER //
CREATE PROCEDURE drop_division_department_fk()
BEGIN
    IF EXISTS (
        SELECT 1
          FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME   = 'division'
           AND COLUMN_NAME  = 'department_id'
    ) THEN
        -- Drop FK constraint first if it exists.
        SET @fk := (
            SELECT CONSTRAINT_NAME
              FROM information_schema.KEY_COLUMN_USAGE
             WHERE TABLE_SCHEMA       = DATABASE()
               AND TABLE_NAME         = 'division'
               AND COLUMN_NAME        = 'department_id'
               AND REFERENCED_TABLE_NAME IS NOT NULL
             LIMIT 1
        );
        IF @fk IS NOT NULL THEN
            SET @sql := CONCAT('ALTER TABLE division DROP FOREIGN KEY ', @fk);
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        ALTER TABLE division DROP COLUMN department_id;
    END IF;
END //
DELIMITER ;

CALL drop_division_department_fk();
DROP PROCEDURE drop_division_department_fk;
