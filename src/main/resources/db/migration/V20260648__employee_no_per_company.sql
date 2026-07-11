-- Move employee numbering from a single global counter (employee_no_seq) to a
-- per-company sequence stored in document_sequence, keyed "{companyId}_EMP".
-- Existing employee numbers are kept; each company continues from its own max,
-- while brand-new companies start at 1000.

-- 1) Drop the old GLOBAL unique index on employee_no. Its name is
--    Hibernate-generated and differs per environment, so resolve it dynamically.
SET @idx := (
    SELECT INDEX_NAME FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'employees'
      AND COLUMN_NAME = 'employee_no'
      AND NON_UNIQUE = 0
      AND INDEX_NAME <> 'PRIMARY'
    LIMIT 1
);
SET @sql := IF(@idx IS NOT NULL,
    CONCAT('ALTER TABLE `employees` DROP INDEX `', @idx, '`'),
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) Add the per-company composite unique key (guarded so re-runs are safe).
SET @has_uk := (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'employees'
      AND CONSTRAINT_NAME = 'uk_employee_company_no'
);
SET @sql2 := IF(@has_uk = 0,
    'ALTER TABLE `employees` ADD CONSTRAINT `uk_employee_company_no` UNIQUE (`company_id`, `employee_no`)',
    'DO 0');
PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- 3) Seed each company's employee-number sequence to (max existing number + 1),
--    never below 1000. Non-numeric numbers (e.g. "ADMIN") cast to 0 and are
--    ignored. next_value is the number the NEXT employee will receive.
INSERT INTO `document_sequence` (`document_type`, `next_value`, `version`)
SELECT CONCAT(c.id, '_EMP'),
       GREATEST(1000, COALESCE(MAX(
           CASE WHEN e.employee_no REGEXP '^[0-9]+$'
                THEN CAST(e.employee_no AS UNSIGNED) END), 999) + 1),
       0
FROM `companies` c
LEFT JOIN `employees` e ON e.company_id = c.id
GROUP BY c.id
ON DUPLICATE KEY UPDATE `next_value` = GREATEST(`next_value`, VALUES(`next_value`));
