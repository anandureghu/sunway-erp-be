-- Division belongs to Department (subcategory under category).
-- Reverses departments.division_id from V20260515.

ALTER TABLE division
    ADD COLUMN department_id BIGINT NULL;

ALTER TABLE division
    ADD CONSTRAINT fk_division_department
        FOREIGN KEY (department_id) REFERENCES departments(id);

-- Map existing links: department had division_id → division gets department_id.
UPDATE division v
INNER JOIN (
    SELECT division_id, MIN(id) AS dept_id
    FROM departments
    WHERE division_id IS NOT NULL
    GROUP BY division_id
) d ON d.division_id = v.id
SET v.department_id = d.dept_id;

DROP PROCEDURE IF EXISTS drop_department_division_fk;
DELIMITER //
CREATE PROCEDURE drop_department_division_fk()
BEGIN
    IF EXISTS (
        SELECT 1
          FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME   = 'departments'
           AND COLUMN_NAME  = 'division_id'
    ) THEN
        SET @fk := (
            SELECT CONSTRAINT_NAME
              FROM information_schema.KEY_COLUMN_USAGE
             WHERE TABLE_SCHEMA       = DATABASE()
               AND TABLE_NAME         = 'departments'
               AND COLUMN_NAME        = 'division_id'
               AND REFERENCED_TABLE_NAME IS NOT NULL
             LIMIT 1
        );
        IF @fk IS NOT NULL THEN
            SET @sql := CONCAT('ALTER TABLE departments DROP FOREIGN KEY ', @fk);
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        ALTER TABLE departments DROP COLUMN division_id;
    END IF;
END //
DELIMITER ;

CALL drop_department_division_fk();
DROP PROCEDURE drop_department_division_fk;

-- Optional division on employee current job (sub-unit within department).
ALTER TABLE employee_current_job
    ADD COLUMN division_id BIGINT NULL;

ALTER TABLE employee_current_job
    ADD CONSTRAINT fk_current_job_division
        FOREIGN KEY (division_id) REFERENCES division(id);
