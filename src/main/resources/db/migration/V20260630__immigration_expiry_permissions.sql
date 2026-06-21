-- Ensure IMMIGRATION module permissions exist for baseline company roles so the
-- company-wide expiry report (/api/immigration/expiring) is authorized in
-- production. Idempotent: safe on every deploy.

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
  'IMMIGRATION',
  CASE WHEN LOWER(cr.name) = 'employee' THEN b'1' ELSE b'0' END,
  CASE WHEN LOWER(cr.name) IN ('admin', 'super admin', 'hr') THEN b'1' ELSE b'0' END,
  CASE WHEN LOWER(cr.name) IN ('admin', 'super admin', 'hr') THEN b'1' ELSE b'0' END,
  CASE
    WHEN LOWER(cr.name) IN ('admin', 'super admin', 'hr') THEN b'1'
    WHEN LOWER(cr.name) = 'employee' THEN b'1'
    ELSE b'0'
  END,
  CASE
    WHEN LOWER(cr.name) IN ('admin', 'super admin') THEN b'1'
    WHEN LOWER(cr.name) = 'hr' THEN b'1'
    ELSE b'0'
  END,
  b'0'
FROM company_roles cr
WHERE LOWER(cr.name) IN ('admin', 'super admin', 'hr', 'employee')
  AND NOT EXISTS (
    SELECT 1
    FROM company_role_permissions existing
    WHERE existing.company_role_id = cr.id
      AND existing.module = 'IMMIGRATION'
  );

UPDATE company_role_permissions crp
JOIN company_roles cr ON cr.id = crp.company_role_id
SET
  crp.view_all = b'1',
  crp.create_permission = b'1',
  crp.edit_permission = b'1',
  crp.delete_permission = b'1'
WHERE crp.module = 'IMMIGRATION'
  AND LOWER(cr.name) IN ('admin', 'super admin');

UPDATE company_role_permissions crp
JOIN company_roles cr ON cr.id = crp.company_role_id
SET
  crp.view_own = b'1',
  crp.view_all = b'1',
  crp.create_permission = b'1',
  crp.edit_permission = b'1',
  crp.delete_permission = b'1'
WHERE crp.module = 'IMMIGRATION'
  AND LOWER(cr.name) = 'hr';

UPDATE company_role_permissions crp
JOIN company_roles cr ON cr.id = crp.company_role_id
SET
  crp.view_own = b'1',
  crp.edit_permission = b'1'
WHERE crp.module = 'IMMIGRATION'
  AND LOWER(cr.name) = 'employee';
