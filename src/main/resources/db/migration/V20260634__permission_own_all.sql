-- Split create / edit / delete permissions into "own" and "all" variants so a
-- grant can be scoped to the holder's own records vs. everyone's. View already
-- had own/all; approve stays single. Backfill keeps current behaviour: the old
-- single flag becomes BOTH own and all (no change in effect until an admin
-- narrows a role to own-only).

-- ── company_role_permissions ──────────────────────────────────────────────
ALTER TABLE `company_role_permissions`
  ADD COLUMN `create_own` BIT(1) NOT NULL DEFAULT b'0',
  ADD COLUMN `create_all` BIT(1) NOT NULL DEFAULT b'0',
  ADD COLUMN `edit_own`   BIT(1) NOT NULL DEFAULT b'0',
  ADD COLUMN `edit_all`   BIT(1) NOT NULL DEFAULT b'0',
  ADD COLUMN `delete_own` BIT(1) NOT NULL DEFAULT b'0',
  ADD COLUMN `delete_all` BIT(1) NOT NULL DEFAULT b'0';

UPDATE `company_role_permissions` SET
  `create_own` = `create_permission`, `create_all` = `create_permission`,
  `edit_own`   = `edit_permission`,   `edit_all`   = `edit_permission`,
  `delete_own` = `delete_permission`, `delete_all` = `delete_permission`;

ALTER TABLE `company_role_permissions`
  DROP COLUMN `create_permission`,
  DROP COLUMN `edit_permission`,
  DROP COLUMN `delete_permission`;

-- ── employee_permissions ──────────────────────────────────────────────────
ALTER TABLE `employee_permissions`
  ADD COLUMN `create_own` BIT(1) NOT NULL DEFAULT b'0',
  ADD COLUMN `create_all` BIT(1) NOT NULL DEFAULT b'0',
  ADD COLUMN `edit_own`   BIT(1) NOT NULL DEFAULT b'0',
  ADD COLUMN `edit_all`   BIT(1) NOT NULL DEFAULT b'0',
  ADD COLUMN `delete_own` BIT(1) NOT NULL DEFAULT b'0',
  ADD COLUMN `delete_all` BIT(1) NOT NULL DEFAULT b'0';

UPDATE `employee_permissions` SET
  `create_own` = `create_permission`, `create_all` = `create_permission`,
  `edit_own`   = `edit_permission`,   `edit_all`   = `edit_permission`,
  `delete_own` = `delete_permission`, `delete_all` = `delete_permission`;

ALTER TABLE `employee_permissions`
  DROP COLUMN `create_permission`,
  DROP COLUMN `edit_permission`,
  DROP COLUMN `delete_permission`;

-- ── enum_role_permissions ─────────────────────────────────────────────────
ALTER TABLE `enum_role_permissions`
  ADD COLUMN `create_own` BIT(1) NOT NULL DEFAULT b'0',
  ADD COLUMN `create_all` BIT(1) NOT NULL DEFAULT b'0',
  ADD COLUMN `edit_own`   BIT(1) NOT NULL DEFAULT b'0',
  ADD COLUMN `edit_all`   BIT(1) NOT NULL DEFAULT b'0',
  ADD COLUMN `delete_own` BIT(1) NOT NULL DEFAULT b'0',
  ADD COLUMN `delete_all` BIT(1) NOT NULL DEFAULT b'0';

UPDATE `enum_role_permissions` SET
  `create_own` = `create_permission`, `create_all` = `create_permission`,
  `edit_own`   = `edit_permission`,   `edit_all`   = `edit_permission`,
  `delete_own` = `delete_permission`, `delete_all` = `delete_permission`;

ALTER TABLE `enum_role_permissions`
  DROP COLUMN `create_permission`,
  DROP COLUMN `edit_permission`,
  DROP COLUMN `delete_permission`;
