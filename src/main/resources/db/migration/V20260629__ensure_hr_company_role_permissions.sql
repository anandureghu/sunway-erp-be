-- Ensure baseline HR module permissions are present after the latest deployed
-- migration version.
-- 
-- This intentionally mirrors the earlier HR permission backfill. It exists
-- with a version greater than V20260627 because production environments that
-- already applied V20260626/V20260627 will not run a later-added V20260625
-- while spring.flyway.out-of-order=false.

INSERT INTO company_role_permissions (
  company_role_id,
  module,
  view_own,
  view_all,
  create_permission,
  edit_permission,
  delete_permission,
  approve
)
SELECT
  cr.id,
  m.module,
  CASE
    WHEN LOWER(cr.name) = 'employee'
      AND m.module IN ('EMPLOYEE_PROFILE','CURRENT_JOB','DEPENDENTS','LEAVES','LOANS','IMMIGRATION')
      THEN b'1'
    ELSE b'0'
  END AS view_own,
  CASE
    WHEN LOWER(cr.name) IN ('admin','super admin','hr') THEN b'1'
    ELSE b'0'
  END AS view_all,
  CASE
    WHEN LOWER(cr.name) IN ('admin','super admin','hr') THEN b'1'
    WHEN LOWER(cr.name) = 'employee' AND m.module IN ('LEAVES','LOANS') THEN b'1'
    ELSE b'0'
  END AS create_permission,
  CASE
    WHEN LOWER(cr.name) IN ('admin','super admin','hr') THEN b'1'
    WHEN LOWER(cr.name) = 'employee' AND m.module IN ('EMPLOYEE_PROFILE','DEPENDENTS','IMMIGRATION') THEN b'1'
    ELSE b'0'
  END AS edit_permission,
  CASE
    WHEN LOWER(cr.name) IN ('admin','super admin') THEN b'1'
    WHEN LOWER(cr.name) = 'hr' AND m.module <> 'HR_SETTINGS' THEN b'1'
    ELSE b'0'
  END AS delete_permission,
  CASE
    WHEN LOWER(cr.name) IN ('admin','super admin') THEN b'1'
    WHEN LOWER(cr.name) = 'hr' AND m.module IN ('LEAVES','LOANS','APPRAISAL') THEN b'1'
    ELSE b'0'
  END AS approve
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
    SELECT 1
    FROM company_role_permissions existing
    WHERE existing.company_role_id = cr.id
      AND existing.module = m.module
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
