-- Track whether housing/food still follow company statutory defaults.
-- When true, updating HR Policies housing/food defaults syncs these rows.

ALTER TABLE employee_compensation
    ADD COLUMN housing_follows_company_default TINYINT(1) NOT NULL DEFAULT 1,
    ADD COLUMN food_follows_company_default TINYINT(1) NOT NULL DEFAULT 1;

-- Backfill: still tracking company default only when amount matches (or housing is provided).
UPDATE employee_compensation ec
JOIN employees e ON e.id = ec.employee_id
JOIN companies c ON c.id = e.company_id
SET
    ec.housing_follows_company_default = CASE
        WHEN ec.housing_type <> 'ALLOWANCE' THEN 1
        WHEN ABS(COALESCE(ec.housing_allowance, 0) - COALESCE(c.default_housing_allowance, 0)) < 0.005 THEN 1
        ELSE 0
    END,
    ec.food_follows_company_default = CASE
        WHEN ABS(COALESCE(ec.food_allowance, 0) - COALESCE(c.default_food_allowance, 0)) < 0.005 THEN 1
        ELSE 0
    END;
