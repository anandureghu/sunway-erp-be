-- Per-rule "active" flag for company-role and employee permissions. When false
-- the rule is saved but not enforced (the resolver skips it and falls through
-- to the next precedence layer). Existing rows default to active = true.

ALTER TABLE `company_role_permissions`
  ADD COLUMN `active` BIT(1) NOT NULL DEFAULT b'1';

ALTER TABLE `employee_permissions`
  ADD COLUMN `active` BIT(1) NOT NULL DEFAULT b'1';
