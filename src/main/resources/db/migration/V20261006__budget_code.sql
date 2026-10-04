-- Budget business codes: {fiscalYear}-{budgetType}-{####}, e.g. 2026-OPEX-1000
-- Non-unique: revisions share the same business code across related headers.

ALTER TABLE budget_headers
    ADD COLUMN budget_code VARCHAR(40) NULL AFTER budget_name;

CREATE INDEX idx_budget_headers_company_code
    ON budget_headers (company_id, budget_code);
