-- Per-rule "active" flag for company-role and employee permissions. When false
-- the rule is saved but not enforced (the resolver skips it and falls through
-- to the next precedence layer). Existing rows default to active = true.

SET @add_company_role_active := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `company_role_permissions` ADD COLUMN `active` BIT(1) NOT NULL DEFAULT b''1''',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'company_role_permissions'
      AND COLUMN_NAME = 'active'
);
PREPARE stmt FROM @add_company_role_active;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_employee_active := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `employee_permissions` ADD COLUMN `active` BIT(1) NOT NULL DEFAULT b''1''',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'employee_permissions'
      AND COLUMN_NAME = 'active'
);
PREPARE stmt FROM @add_employee_active;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
