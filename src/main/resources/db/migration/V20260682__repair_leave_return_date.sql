-- Self-healing repair for schema drift: some databases are missing the
-- employee_leaves.return_date column even though V20260619 is recorded as
-- applied (the table was recreated/imported outside Flyway). ddl-auto=validate
-- then fails at boot. Add the column only when it is actually missing so this is
-- a no-op everywhere the column already exists.
SET @has_col := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'employee_leaves'
      AND column_name = 'return_date'
);
SET @sql := IF(@has_col = 0,
    'ALTER TABLE `employee_leaves` ADD COLUMN `return_date` date NULL',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
