-- Idempotent HR permission backfill for baseline company roles (Admin, HR, Employee).
-- Runs on every application startup via CompanyRolePermissionSeeder.
-- Create/Edit/Delete are split into own and all variants. Baseline grants apply
-- to both, so behaviour is unchanged (admins can later narrow a role to own-only).

INSERT INTO company_role_permissions (
  company_role_id,
  module,
  view_own,
  view_all,
  create_own,
  create_all,
  edit_own,
  edit_all,
  delete_own,
  delete_all,
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
  END AS create_own,
  CASE
    WHEN LOWER(cr.name) IN ('admin','super admin','hr') THEN b'1'
    WHEN LOWER(cr.name) = 'employee' AND m.module IN ('LEAVES','LOANS') THEN b'1'
    ELSE b'0'
  END AS create_all,
  CASE
    WHEN LOWER(cr.name) IN ('admin','super admin','hr') THEN b'1'
    WHEN LOWER(cr.name) = 'employee' AND m.module IN ('EMPLOYEE_PROFILE','DEPENDENTS','IMMIGRATION') THEN b'1'
    ELSE b'0'
  END AS edit_own,
  CASE
    WHEN LOWER(cr.name) IN ('admin','super admin','hr') THEN b'1'
    WHEN LOWER(cr.name) = 'employee' AND m.module IN ('EMPLOYEE_PROFILE','DEPENDENTS','IMMIGRATION') THEN b'1'
    ELSE b'0'
  END AS edit_all,
  CASE
    WHEN LOWER(cr.name) IN ('admin','super admin') THEN b'1'
    WHEN LOWER(cr.name) = 'hr' AND m.module <> 'HR_SETTINGS' THEN b'1'
    ELSE b'0'
  END AS delete_own,
  CASE
    WHEN LOWER(cr.name) IN ('admin','super admin') THEN b'1'
    WHEN LOWER(cr.name) = 'hr' AND m.module <> 'HR_SETTINGS' THEN b'1'
    ELSE b'0'
  END AS delete_all,
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

-- ── Finance module permissions ───────────────────────────────────────────────
-- Baseline grants for Admin / Super Admin / Finance Manager / Accountant /
-- Controller / AP/AR Clerk / Auditor. Inserts missing rows only; UPDATEs below
-- keep baseline caps in sync on restart (same pattern as HR).

INSERT INTO company_role_permissions (
  company_role_id,
  module,
  view_own,
  view_all,
  create_own,
  create_all,
  edit_own,
  edit_all,
  delete_own,
  delete_all,
  approve
)
SELECT
  cr.id,
  m.module,
  b'0', b'0', b'0', b'0', b'0', b'0', b'0', b'0', b'0'
FROM company_roles cr
JOIN (
  SELECT 'FINANCE_DASHBOARD' AS module UNION ALL
  SELECT 'FINANCE_INVOICE' UNION ALL
  SELECT 'FINANCE_PAYMENT' UNION ALL
  SELECT 'FINANCE_LEDGER' UNION ALL
  SELECT 'FINANCE_JOURNAL' UNION ALL
  SELECT 'FINANCE_COA' UNION ALL
  SELECT 'FINANCE_BUDGET' UNION ALL
  SELECT 'FINANCE_RECONCILIATION' UNION ALL
  SELECT 'FINANCE_REPORTS'
) m
WHERE LOWER(cr.name) IN (
  'admin', 'super admin', 'finance manager', 'accountant',
  'controller', 'ap/ar clerk', 'auditor (external)'
)
  AND NOT EXISTS (
    SELECT 1
    FROM company_role_permissions existing
    WHERE existing.company_role_id = cr.id
      AND existing.module = m.module
  );

-- Admin / Super Admin — full finance
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
WHERE LOWER(cr.name) IN ('admin', 'super admin')
  AND crp.module IN (
    'FINANCE_DASHBOARD','FINANCE_INVOICE','FINANCE_PAYMENT','FINANCE_LEDGER',
    'FINANCE_JOURNAL','FINANCE_COA','FINANCE_BUDGET','FINANCE_RECONCILIATION',
    'FINANCE_REPORTS'
  );

-- Finance Manager — full finance ops (dashboard view-only caps stay view)
UPDATE company_role_permissions crp
JOIN company_roles cr ON cr.id = crp.company_role_id
SET
  crp.view_own = b'1',
  crp.view_all = b'1',
  crp.create_own = CASE WHEN crp.module = 'FINANCE_DASHBOARD' THEN b'0' ELSE b'1' END,
  crp.create_all = CASE WHEN crp.module = 'FINANCE_DASHBOARD' THEN b'0' ELSE b'1' END,
  crp.edit_own = CASE WHEN crp.module = 'FINANCE_DASHBOARD' THEN b'0' ELSE b'1' END,
  crp.edit_all = CASE WHEN crp.module = 'FINANCE_DASHBOARD' THEN b'0' ELSE b'1' END,
  crp.delete_own = CASE WHEN crp.module = 'FINANCE_DASHBOARD' THEN b'0' ELSE b'1' END,
  crp.delete_all = CASE WHEN crp.module = 'FINANCE_DASHBOARD' THEN b'0' ELSE b'1' END,
  crp.approve = CASE WHEN crp.module = 'FINANCE_DASHBOARD' THEN b'0' ELSE b'1' END
WHERE LOWER(cr.name) = 'finance manager'
  AND crp.module IN (
    'FINANCE_DASHBOARD','FINANCE_INVOICE','FINANCE_PAYMENT','FINANCE_LEDGER',
    'FINANCE_JOURNAL','FINANCE_COA','FINANCE_BUDGET','FINANCE_RECONCILIATION',
    'FINANCE_REPORTS'
  );

-- Accountant — create/edit GL + AP/AR; no delete/approve on most modules
UPDATE company_role_permissions crp
JOIN company_roles cr ON cr.id = crp.company_role_id
SET
  crp.view_own = b'1',
  crp.view_all = b'1',
  crp.create_own = CASE
    WHEN crp.module IN ('FINANCE_JOURNAL','FINANCE_LEDGER','FINANCE_COA','FINANCE_RECONCILIATION','FINANCE_INVOICE','FINANCE_PAYMENT','FINANCE_BUDGET')
      THEN b'1' ELSE b'0' END,
  crp.create_all = CASE
    WHEN crp.module IN ('FINANCE_JOURNAL','FINANCE_LEDGER','FINANCE_COA','FINANCE_RECONCILIATION','FINANCE_INVOICE','FINANCE_PAYMENT','FINANCE_BUDGET')
      THEN b'1' ELSE b'0' END,
  crp.edit_own = CASE
    WHEN crp.module IN ('FINANCE_JOURNAL','FINANCE_LEDGER','FINANCE_COA','FINANCE_RECONCILIATION','FINANCE_INVOICE','FINANCE_PAYMENT','FINANCE_BUDGET')
      THEN b'1' ELSE b'0' END,
  crp.edit_all = CASE
    WHEN crp.module IN ('FINANCE_JOURNAL','FINANCE_LEDGER','FINANCE_COA','FINANCE_RECONCILIATION','FINANCE_INVOICE','FINANCE_PAYMENT','FINANCE_BUDGET')
      THEN b'1' ELSE b'0' END,
  crp.delete_own = b'0',
  crp.delete_all = b'0',
  crp.approve = b'0'
WHERE LOWER(cr.name) = 'accountant'
  AND crp.module IN (
    'FINANCE_DASHBOARD','FINANCE_INVOICE','FINANCE_PAYMENT','FINANCE_LEDGER',
    'FINANCE_JOURNAL','FINANCE_COA','FINANCE_BUDGET','FINANCE_RECONCILIATION',
    'FINANCE_REPORTS'
  );

-- Controller — view all + approve key finance modules
UPDATE company_role_permissions crp
JOIN company_roles cr ON cr.id = crp.company_role_id
SET
  crp.view_own = b'1',
  crp.view_all = b'1',
  crp.create_own = b'0',
  crp.create_all = b'0',
  crp.edit_own = b'0',
  crp.edit_all = b'0',
  crp.delete_own = b'0',
  crp.delete_all = b'0',
  crp.approve = CASE
    WHEN crp.module IN ('FINANCE_JOURNAL','FINANCE_LEDGER','FINANCE_BUDGET','FINANCE_RECONCILIATION','FINANCE_INVOICE','FINANCE_PAYMENT')
      THEN b'1' ELSE b'0' END
WHERE LOWER(cr.name) = 'controller'
  AND crp.module IN (
    'FINANCE_DASHBOARD','FINANCE_INVOICE','FINANCE_PAYMENT','FINANCE_LEDGER',
    'FINANCE_JOURNAL','FINANCE_COA','FINANCE_BUDGET','FINANCE_RECONCILIATION',
    'FINANCE_REPORTS'
  );

-- AP/AR Clerk — invoices & payments only
UPDATE company_role_permissions crp
JOIN company_roles cr ON cr.id = crp.company_role_id
SET
  crp.view_own = CASE
    WHEN crp.module IN ('FINANCE_INVOICE','FINANCE_PAYMENT','FINANCE_DASHBOARD') THEN b'1' ELSE b'0' END,
  crp.view_all = CASE
    WHEN crp.module IN ('FINANCE_INVOICE','FINANCE_PAYMENT','FINANCE_DASHBOARD') THEN b'1' ELSE b'0' END,
  crp.create_own = CASE
    WHEN crp.module IN ('FINANCE_INVOICE','FINANCE_PAYMENT') THEN b'1' ELSE b'0' END,
  crp.create_all = CASE
    WHEN crp.module IN ('FINANCE_INVOICE','FINANCE_PAYMENT') THEN b'1' ELSE b'0' END,
  crp.edit_own = CASE
    WHEN crp.module IN ('FINANCE_INVOICE','FINANCE_PAYMENT') THEN b'1' ELSE b'0' END,
  crp.edit_all = CASE
    WHEN crp.module IN ('FINANCE_INVOICE','FINANCE_PAYMENT') THEN b'1' ELSE b'0' END,
  crp.delete_own = b'0',
  crp.delete_all = b'0',
  crp.approve = b'0'
WHERE LOWER(cr.name) = 'ap/ar clerk'
  AND crp.module IN (
    'FINANCE_DASHBOARD','FINANCE_INVOICE','FINANCE_PAYMENT','FINANCE_LEDGER',
    'FINANCE_JOURNAL','FINANCE_COA','FINANCE_BUDGET','FINANCE_RECONCILIATION',
    'FINANCE_REPORTS'
  );

-- Auditor (External) — view-only across finance
UPDATE company_role_permissions crp
JOIN company_roles cr ON cr.id = crp.company_role_id
SET
  crp.view_own = b'1',
  crp.view_all = b'1',
  crp.create_own = b'0',
  crp.create_all = b'0',
  crp.edit_own = b'0',
  crp.edit_all = b'0',
  crp.delete_own = b'0',
  crp.delete_all = b'0',
  crp.approve = b'0'
WHERE LOWER(cr.name) = 'auditor (external)'
  AND crp.module IN (
    'FINANCE_DASHBOARD','FINANCE_INVOICE','FINANCE_PAYMENT','FINANCE_LEDGER',
    'FINANCE_JOURNAL','FINANCE_COA','FINANCE_BUDGET','FINANCE_RECONCILIATION',
    'FINANCE_REPORTS'
  );

-- Finance Manager — supplier approve/reject under Finance Settings (vendors API)
INSERT INTO company_role_permissions (
  company_role_id, module,
  view_own, view_all, create_own, create_all,
  edit_own, edit_all, delete_own, delete_all, approve
)
SELECT cr.id, 'INVENTORY_PURCHASE',
       b'1', b'1', b'0', b'0', b'1', b'1', b'0', b'0', b'1'
FROM company_roles cr
WHERE LOWER(cr.name) = 'finance manager'
  AND NOT EXISTS (
    SELECT 1 FROM company_role_permissions e
    WHERE e.company_role_id = cr.id AND e.module = 'INVENTORY_PURCHASE'
  );

UPDATE company_role_permissions crp
JOIN company_roles cr ON cr.id = crp.company_role_id
SET
  crp.view_own = b'1',
  crp.view_all = b'1',
  crp.edit_own = b'1',
  crp.edit_all = b'1',
  crp.approve = b'1'
WHERE LOWER(cr.name) = 'finance manager'
  AND crp.module = 'INVENTORY_PURCHASE';

-- ── Dashboard permissions ────────────────────────────────────────────────────
-- Each area's sidebar dashboard is a dedicated, view-only permission
-- (HR_DASHBOARD / FINANCE_DASHBOARD / INVENTORY_DASHBOARD) so admins can grant or
-- revoke it explicitly. Backfill: any company role that can already view a core
-- page in an area keeps that area's dashboard, with the same view scope. Only
-- inserts when the role has no dashboard row yet, so later admin edits are kept.

-- HR dashboard → roles that can view any core HR page.
INSERT INTO company_role_permissions (
  company_role_id, module,
  view_own, view_all, create_own, create_all,
  edit_own, edit_all, delete_own, delete_all, approve
)
SELECT src.company_role_id, 'HR_DASHBOARD',
       src.view_own, src.view_all, b'0', b'0', b'0', b'0', b'0', b'0', b'0'
FROM (
  SELECT company_role_id,
         MAX(view_own + 0) AS view_own,
         MAX(view_all + 0) AS view_all
  FROM company_role_permissions
  WHERE module IN ('EMPLOYEE_PROFILE','HR_REPORTS','LEAVES','PAYROLL')
    AND (view_own = b'1' OR view_all = b'1')
  GROUP BY company_role_id
) src
WHERE NOT EXISTS (
  SELECT 1 FROM company_role_permissions e
  WHERE e.company_role_id = src.company_role_id AND e.module = 'HR_DASHBOARD'
);

-- Finance dashboard → roles that can view any core finance page.
INSERT INTO company_role_permissions (
  company_role_id, module,
  view_own, view_all, create_own, create_all,
  edit_own, edit_all, delete_own, delete_all, approve
)
SELECT src.company_role_id, 'FINANCE_DASHBOARD',
       src.view_own, src.view_all, b'0', b'0', b'0', b'0', b'0', b'0', b'0'
FROM (
  SELECT company_role_id,
         MAX(view_own + 0) AS view_own,
         MAX(view_all + 0) AS view_all
  FROM company_role_permissions
  WHERE module IN (
      'FINANCE_REPORTS','FINANCE_INVOICE','FINANCE_PAYMENT','FINANCE_LEDGER',
      'FINANCE_JOURNAL','FINANCE_COA','FINANCE_BUDGET','FINANCE_RECONCILIATION'
    )
    AND (view_own = b'1' OR view_all = b'1')
  GROUP BY company_role_id
) src
WHERE NOT EXISTS (
  SELECT 1 FROM company_role_permissions e
  WHERE e.company_role_id = src.company_role_id AND e.module = 'FINANCE_DASHBOARD'
);

-- Inventory dashboard → roles that can view any core inventory page.
INSERT INTO company_role_permissions (
  company_role_id, module,
  view_own, view_all, create_own, create_all,
  edit_own, edit_all, delete_own, delete_all, approve
)
SELECT src.company_role_id, 'INVENTORY_DASHBOARD',
       src.view_own, src.view_all, b'0', b'0', b'0', b'0', b'0', b'0', b'0'
FROM (
  SELECT company_role_id,
         MAX(view_own + 0) AS view_own,
         MAX(view_all + 0) AS view_all
  FROM company_role_permissions
  WHERE module IN ('INVENTORY_STOCK','INVENTORY_ITEM','INVENTORY_SALES','INVENTORY_PURCHASE')
    AND (view_own = b'1' OR view_all = b'1')
  GROUP BY company_role_id
) src
WHERE NOT EXISTS (
  SELECT 1 FROM company_role_permissions e
  WHERE e.company_role_id = src.company_role_id AND e.module = 'INVENTORY_DASHBOARD'
);
