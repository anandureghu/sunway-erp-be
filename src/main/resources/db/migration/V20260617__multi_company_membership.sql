-- Allow one user to belong to multiple companies via multiple employee records.

-- 1. Add per-company HR role on employee (idempotent for failed prior attempt)
SET @add_col := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `employees` ADD COLUMN `company_role_id` bigint DEFAULT NULL AFTER `user_id`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'employees'
      AND COLUMN_NAME = 'company_role_id'
);
PREPARE stmt FROM @add_col;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. Backfill from users
UPDATE `employees` e
    INNER JOIN `users` u ON e.user_id = u.id
SET e.company_role_id = u.company_role_id
WHERE e.company_role_id IS NULL AND u.company_role_id IS NOT NULL;

-- 3. Drop FK on user_id (MySQL requires this before dropping the unique index it uses)
SET @drop_fk := (
    SELECT IF(
        COUNT(*) > 0,
        'ALTER TABLE `employees` DROP FOREIGN KEY `FK69x3vjuy1t5p18a5llb8h2fjx`',
        'SELECT 1'
    )
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'employees'
      AND CONSTRAINT_NAME = 'FK69x3vjuy1t5p18a5llb8h2fjx'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
PREPARE stmt FROM @drop_fk;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. Drop unique constraint on user_id (one user → many employees)
SET @drop_uk := (
    SELECT IF(
        COUNT(*) > 0,
        'ALTER TABLE `employees` DROP INDEX `UKj2dmgsma6pont6kf7nic9elpd`',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'employees'
      AND INDEX_NAME = 'UKj2dmgsma6pont6kf7nic9elpd'
);
PREPARE stmt FROM @drop_uk;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5. Non-unique index on user_id (required before re-adding FK)
SET @add_idx := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `employees` ADD INDEX `idx_employees_user_id` (`user_id`)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'employees'
      AND INDEX_NAME = 'idx_employees_user_id'
);
PREPARE stmt FROM @add_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 6. Re-create FK on user_id (no longer requires uniqueness)
SET @add_fk := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `employees` ADD CONSTRAINT `FK69x3vjuy1t5p18a5llb8h2fjx` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)',
        'SELECT 1'
    )
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'employees'
      AND CONSTRAINT_NAME = 'FK69x3vjuy1t5p18a5llb8h2fjx'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
PREPARE stmt FROM @add_fk;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 7. Prevent duplicate membership in the same company
SET @add_uk := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `employees` ADD UNIQUE KEY `uk_employee_user_company` (`user_id`, `company_id`)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'employees'
      AND INDEX_NAME = 'uk_employee_user_company'
);
PREPARE stmt FROM @add_uk;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 8. FK for company_role_id
SET @add_role_fk := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `employees` ADD CONSTRAINT `FK_employees_company_role` FOREIGN KEY (`company_role_id`) REFERENCES `company_roles` (`id`)',
        'SELECT 1'
    )
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'employees'
      AND CONSTRAINT_NAME = 'FK_employees_company_role'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
PREPARE stmt FROM @add_role_fk;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
