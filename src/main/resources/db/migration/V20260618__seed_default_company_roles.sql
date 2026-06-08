-- Backfill baseline HR roles for companies that have none (or are missing defaults).

INSERT INTO company_roles (name, description, active, company_id, created_date, updated_date)
SELECT 'Admin', 'Company administrator', 1, c.id, NOW(), NOW()
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM company_roles cr
    WHERE cr.company_id = c.id AND LOWER(cr.name) = 'admin'
);

INSERT INTO company_roles (name, description, active, company_id, created_date, updated_date)
SELECT 'Employee', 'Standard employee', 1, c.id, NOW(), NOW()
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM company_roles cr
    WHERE cr.company_id = c.id AND LOWER(cr.name) = 'employee'
);

INSERT INTO company_roles (name, description, active, company_id, created_date, updated_date)
SELECT 'HR', 'Human resources', 1, c.id, NOW(), NOW()
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM company_roles cr
    WHERE cr.company_id = c.id AND LOWER(cr.name) = 'hr'
);
