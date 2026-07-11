-- Backfill baseline HR module permissions for existing company roles.
-- Supports both legacy (create_permission) and own/all permission schemas.

DROP PROCEDURE IF EXISTS backfill_hr_company_role_permissions;
DELIMITER //
CREATE PROCEDURE backfill_hr_company_role_permissions()
BEGIN
    DECLARE uses_legacy_schema TINYINT DEFAULT 0;

    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'company_role_permissions'
           AND COLUMN_NAME = 'create_permission'
    ) THEN
        SET uses_legacy_schema = 1;
    END IF;

    IF uses_legacy_schema = 1 THEN
        INSERT INTO company_role_permissions (
          company_role_id, module, view_own, view_all,
          create_permission, edit_permission, delete_permission, approve
        )
        SELECT
          cr.id,
          m.module,
          CASE
            WHEN LOWER(cr.name) = 'employee'
              AND m.module IN ('EMPLOYEE_PROFILE','CURRENT_JOB','DEPENDENTS','LEAVES','LOANS','IMMIGRATION')
              THEN b'1'
            ELSE b'0'
          END,
          CASE WHEN LOWER(cr.name) IN ('admin','super admin','hr') THEN b'1' ELSE b'0' END,
          CASE
            WHEN LOWER(cr.name) IN ('admin','super admin','hr') THEN b'1'
            WHEN LOWER(cr.name) = 'employee' AND m.module IN ('LEAVES','LOANS') THEN b'1'
            ELSE b'0'
          END,
          CASE
            WHEN LOWER(cr.name) IN ('admin','super admin','hr') THEN b'1'
            WHEN LOWER(cr.name) = 'employee' AND m.module IN ('EMPLOYEE_PROFILE','DEPENDENTS','IMMIGRATION') THEN b'1'
            ELSE b'0'
          END,
          CASE
            WHEN LOWER(cr.name) IN ('admin','super admin') THEN b'1'
            WHEN LOWER(cr.name) = 'hr' AND m.module <> 'HR_SETTINGS' THEN b'1'
            ELSE b'0'
          END,
          CASE
            WHEN LOWER(cr.name) IN ('admin','super admin') THEN b'1'
            WHEN LOWER(cr.name) = 'hr' AND m.module IN ('LEAVES','LOANS','APPRAISAL') THEN b'1'
            ELSE b'0'
          END
        FROM company_roles cr
        JOIN (
          SELECT 'EMPLOYEE_PROFILE' AS module UNION ALL
          SELECT 'CURRENT_JOB' UNION ALL
          SELECT 'DEPENDENTS' UNION ALL
          SELECT 'IMMIGRATION' UNION ALL
          SELECT 'SALARY' UNION ALL
          SELECT 'PAYROLL' UNION ALL
          SELECT 'LEAVES' UNION ALL
          SELECT 'LOANS' UNION ALL
          SELECT 'APPRAISAL' UNION ALL
          SELECT 'HR_REPORTS' UNION ALL
          SELECT 'HR_SETTINGS'
        ) m
        WHERE LOWER(cr.name) IN ('admin','super admin','hr','employee')
          AND NOT EXISTS (
            SELECT 1 FROM company_role_permissions existing
            WHERE existing.company_role_id = cr.id AND existing.module = m.module
          );

        UPDATE company_role_permissions crp
        JOIN company_roles cr ON cr.id = crp.company_role_id
        SET
          crp.view_own = b'1',
          crp.view_all = b'1',
          crp.create_permission = b'1',
          crp.edit_permission = b'1',
          crp.delete_permission = b'1',
          crp.approve = b'1'
        WHERE LOWER(cr.name) IN ('admin','super admin')
          AND crp.module IN (
            'EMPLOYEE_PROFILE','CURRENT_JOB','DEPENDENTS','IMMIGRATION','SALARY',
            'PAYROLL','LEAVES','LOANS','APPRAISAL','HR_REPORTS','HR_SETTINGS'
          );

        UPDATE company_role_permissions crp
        JOIN company_roles cr ON cr.id = crp.company_role_id
        SET
          crp.view_own = b'1',
          crp.view_all = b'1',
          crp.create_permission = b'1',
          crp.edit_permission = b'1',
          crp.delete_permission = CASE WHEN crp.module = 'HR_SETTINGS' THEN b'0' ELSE b'1' END,
          crp.approve = CASE WHEN crp.module IN ('LEAVES','LOANS','APPRAISAL') THEN b'1' ELSE b'0' END
        WHERE LOWER(cr.name) = 'hr'
          AND crp.module IN (
            'EMPLOYEE_PROFILE','CURRENT_JOB','DEPENDENTS','IMMIGRATION','SALARY',
            'PAYROLL','LEAVES','LOANS','APPRAISAL','HR_REPORTS','HR_SETTINGS'
          );
    ELSE
        INSERT INTO company_role_permissions (
          company_role_id, module, view_own, view_all,
          create_own, create_all, edit_own, edit_all, delete_own, delete_all, approve
        )
        SELECT
          cr.id,
          m.module,
          CASE
            WHEN LOWER(cr.name) = 'employee'
              AND m.module IN ('EMPLOYEE_PROFILE','CURRENT_JOB','DEPENDENTS','LEAVES','LOANS','IMMIGRATION')
              THEN b'1'
            ELSE b'0'
          END,
          CASE WHEN LOWER(cr.name) IN ('admin','super admin','hr') THEN b'1' ELSE b'0' END,
          CASE
            WHEN LOWER(cr.name) IN ('admin','super admin','hr') THEN b'1'
            WHEN LOWER(cr.name) = 'employee' AND m.module IN ('LEAVES','LOANS') THEN b'1'
            ELSE b'0'
          END,
          CASE
            WHEN LOWER(cr.name) IN ('admin','super admin','hr') THEN b'1'
            WHEN LOWER(cr.name) = 'employee' AND m.module IN ('LEAVES','LOANS') THEN b'1'
            ELSE b'0'
          END,
          CASE
            WHEN LOWER(cr.name) IN ('admin','super admin','hr') THEN b'1'
            WHEN LOWER(cr.name) = 'employee' AND m.module IN ('EMPLOYEE_PROFILE','DEPENDENTS','IMMIGRATION') THEN b'1'
            ELSE b'0'
          END,
          CASE
            WHEN LOWER(cr.name) IN ('admin','super admin','hr') THEN b'1'
            WHEN LOWER(cr.name) = 'employee' AND m.module IN ('EMPLOYEE_PROFILE','DEPENDENTS','IMMIGRATION') THEN b'1'
            ELSE b'0'
          END,
          CASE
            WHEN LOWER(cr.name) IN ('admin','super admin') THEN b'1'
            WHEN LOWER(cr.name) = 'hr' AND m.module <> 'HR_SETTINGS' THEN b'1'
            ELSE b'0'
          END,
          CASE
            WHEN LOWER(cr.name) IN ('admin','super admin') THEN b'1'
            WHEN LOWER(cr.name) = 'hr' AND m.module <> 'HR_SETTINGS' THEN b'1'
            ELSE b'0'
          END,
          CASE
            WHEN LOWER(cr.name) IN ('admin','super admin') THEN b'1'
            WHEN LOWER(cr.name) = 'hr' AND m.module IN ('LEAVES','LOANS','APPRAISAL') THEN b'1'
            ELSE b'0'
          END
        FROM company_roles cr
        JOIN (
          SELECT 'EMPLOYEE_PROFILE' AS module UNION ALL
          SELECT 'CURRENT_JOB' UNION ALL
          SELECT 'DEPENDENTS' UNION ALL
          SELECT 'IMMIGRATION' UNION ALL
          SELECT 'SALARY' UNION ALL
          SELECT 'PAYROLL' UNION ALL
          SELECT 'LEAVES' UNION ALL
          SELECT 'LOANS' UNION ALL
          SELECT 'APPRAISAL' UNION ALL
          SELECT 'HR_REPORTS' UNION ALL
          SELECT 'HR_SETTINGS'
        ) m
        WHERE LOWER(cr.name) IN ('admin','super admin','hr','employee')
          AND NOT EXISTS (
            SELECT 1 FROM company_role_permissions existing
            WHERE existing.company_role_id = cr.id AND existing.module = m.module
          );

        UPDATE company_role_permissions crp
        JOIN company_roles cr ON cr.id = crp.company_role_id
        SET
          crp.view_own = b'1',
          crp.view_all = b'1',
          crp.create_own = b'1',
          crp.create_all = b'1',
          crp.edit_own = b'1',
          crp.edit_all = b'1',
          crp.delete_own = b'1',
          crp.delete_all = b'1',
          crp.approve = b'1'
        WHERE LOWER(cr.name) IN ('admin','super admin')
          AND crp.module IN (
            'EMPLOYEE_PROFILE','CURRENT_JOB','DEPENDENTS','IMMIGRATION','SALARY',
            'PAYROLL','LEAVES','LOANS','APPRAISAL','HR_REPORTS','HR_SETTINGS'
          );

        UPDATE company_role_permissions crp
        JOIN company_roles cr ON cr.id = crp.company_role_id
        SET
          crp.view_own = b'1',
          crp.view_all = b'1',
          crp.create_own = b'1',
          crp.create_all = b'1',
          crp.edit_own = b'1',
          crp.edit_all = b'1',
          crp.delete_own = CASE WHEN crp.module = 'HR_SETTINGS' THEN b'0' ELSE b'1' END,
          crp.delete_all = CASE WHEN crp.module = 'HR_SETTINGS' THEN b'0' ELSE b'1' END,
          crp.approve = CASE WHEN crp.module IN ('LEAVES','LOANS','APPRAISAL') THEN b'1' ELSE b'0' END
        WHERE LOWER(cr.name) = 'hr'
          AND crp.module IN (
            'EMPLOYEE_PROFILE','CURRENT_JOB','DEPENDENTS','IMMIGRATION','SALARY',
            'PAYROLL','LEAVES','LOANS','APPRAISAL','HR_REPORTS','HR_SETTINGS'
          );
    END IF;
END //
DELIMITER ;

CALL backfill_hr_company_role_permissions();
DROP PROCEDURE backfill_hr_company_role_permissions;
