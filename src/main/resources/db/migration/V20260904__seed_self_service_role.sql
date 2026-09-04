-- Ensure every existing company has the default self-service employee role, so new
-- hires can be defaulted to it. New companies get it seeded in code on creation.
INSERT INTO company_roles (name, description, active, company_id, created_date, updated_date)
SELECT 'Employee (self service)',
       'Default self-service role assigned to new employees',
       b'1', c.id, NOW(6), NOW(6)
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM company_roles r
    WHERE r.company_id = c.id
      AND LOWER(r.name) = 'employee (self service)'
);
