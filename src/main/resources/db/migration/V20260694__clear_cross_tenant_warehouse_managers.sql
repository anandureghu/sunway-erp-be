-- Warehouse.manager stores a users.id, but after multi-company membership the
-- authoritative tenant link is employees.(user_id, company_id). Clear any
-- warehouse manager FKs that point at a user with no membership in the
-- warehouse's company (same for department/division employee manager FKs).

UPDATE warehouses w
LEFT JOIN employees e
       ON e.user_id = w.manager
      AND e.company_id = w.company_id
SET w.manager = NULL
WHERE w.manager IS NOT NULL
  AND e.id IS NULL;

UPDATE departments d
LEFT JOIN employees e
       ON e.id = d.manager_id
      AND e.company_id = d.company_id
SET d.manager_id = NULL
WHERE d.manager_id IS NOT NULL
  AND e.id IS NULL;

UPDATE division d
LEFT JOIN employees e
       ON e.id = d.manager_id
      AND e.company_id = d.company_id
SET d.manager_id = NULL
WHERE d.manager_id IS NOT NULL
  AND e.id IS NULL;
