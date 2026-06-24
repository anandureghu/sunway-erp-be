-- Repair environments where V20260634 is recorded in flyway_schema_history but the
-- own/all permission columns were never applied (schema drift). Idempotent: no-op when
-- V20260634 already ran successfully.

DROP PROCEDURE IF EXISTS repair_permission_own_all_columns;
DELIMITER //
CREATE PROCEDURE repair_permission_own_all_columns()
BEGIN
    -- ── company_role_permissions ─────────────────────────────────────────────
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME   = 'company_role_permissions'
           AND COLUMN_NAME  = 'create_own'
    ) THEN
        ALTER TABLE company_role_permissions
          ADD COLUMN create_own  BIT(1) NOT NULL DEFAULT b'0',
          ADD COLUMN create_all  BIT(1) NOT NULL DEFAULT b'0',
          ADD COLUMN edit_own    BIT(1) NOT NULL DEFAULT b'0',
          ADD COLUMN edit_all    BIT(1) NOT NULL DEFAULT b'0',
          ADD COLUMN delete_own  BIT(1) NOT NULL DEFAULT b'0',
          ADD COLUMN delete_all  BIT(1) NOT NULL DEFAULT b'0';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME   = 'company_role_permissions'
           AND COLUMN_NAME  = 'create_permission'
    ) THEN
        UPDATE company_role_permissions SET
          create_own  = create_permission,
          create_all  = create_permission,
          edit_own    = edit_permission,
          edit_all    = edit_permission,
          delete_own  = delete_permission,
          delete_all  = delete_permission;

        ALTER TABLE company_role_permissions
          DROP COLUMN create_permission,
          DROP COLUMN edit_permission,
          DROP COLUMN delete_permission;
    END IF;

    -- ── employee_permissions ───────────────────────────────────────────────
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME   = 'employee_permissions'
           AND COLUMN_NAME  = 'create_own'
    ) THEN
        ALTER TABLE employee_permissions
          ADD COLUMN create_own  BIT(1) NOT NULL DEFAULT b'0',
          ADD COLUMN create_all  BIT(1) NOT NULL DEFAULT b'0',
          ADD COLUMN edit_own    BIT(1) NOT NULL DEFAULT b'0',
          ADD COLUMN edit_all    BIT(1) NOT NULL DEFAULT b'0',
          ADD COLUMN delete_own  BIT(1) NOT NULL DEFAULT b'0',
          ADD COLUMN delete_all  BIT(1) NOT NULL DEFAULT b'0';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME   = 'employee_permissions'
           AND COLUMN_NAME  = 'create_permission'
    ) THEN
        UPDATE employee_permissions SET
          create_own  = create_permission,
          create_all  = create_permission,
          edit_own    = edit_permission,
          edit_all    = edit_permission,
          delete_own  = delete_permission,
          delete_all  = delete_permission;

        ALTER TABLE employee_permissions
          DROP COLUMN create_permission,
          DROP COLUMN edit_permission,
          DROP COLUMN delete_permission;
    END IF;

    -- ── enum_role_permissions ──────────────────────────────────────────────
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME   = 'enum_role_permissions'
           AND COLUMN_NAME  = 'create_own'
    ) THEN
        ALTER TABLE enum_role_permissions
          ADD COLUMN create_own  BIT(1) NOT NULL DEFAULT b'0',
          ADD COLUMN create_all  BIT(1) NOT NULL DEFAULT b'0',
          ADD COLUMN edit_own    BIT(1) NOT NULL DEFAULT b'0',
          ADD COLUMN edit_all    BIT(1) NOT NULL DEFAULT b'0',
          ADD COLUMN delete_own  BIT(1) NOT NULL DEFAULT b'0',
          ADD COLUMN delete_all  BIT(1) NOT NULL DEFAULT b'0';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME   = 'enum_role_permissions'
           AND COLUMN_NAME  = 'create_permission'
    ) THEN
        UPDATE enum_role_permissions SET
          create_own  = create_permission,
          create_all  = create_permission,
          edit_own    = edit_permission,
          edit_all    = edit_permission,
          delete_own  = delete_permission,
          delete_all  = delete_permission;

        ALTER TABLE enum_role_permissions
          DROP COLUMN create_permission,
          DROP COLUMN edit_permission,
          DROP COLUMN delete_permission;
    END IF;
END //
DELIMITER ;

CALL repair_permission_own_all_columns();
DROP PROCEDURE repair_permission_own_all_columns;
