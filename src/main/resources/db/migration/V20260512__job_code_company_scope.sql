-- =============================================================================
-- Job codes: convert from global to company-scoped.
-- Run ONCE, before booting with the new entity definition. Hibernate ddl-auto
-- (update) cannot drop the old UNIQUE(code) index or backfill existing rows.
-- =============================================================================

-- 1. Add company_id as nullable so existing rows survive.
ALTER TABLE job_codes
    ADD COLUMN company_id BIGINT NULL;

-- 2. Backfill existing rows to the lowest-id company (the "primary" tenant).
--    Adjust this WHERE/SET if you want a different company to own legacy codes.
UPDATE job_codes
   SET company_id = (SELECT MIN(id) FROM companies)
 WHERE company_id IS NULL;

-- 3. Enforce NOT NULL now that every row has an owner.
ALTER TABLE job_codes
    MODIFY COLUMN company_id BIGINT NOT NULL;

-- 4. Drop the old global UNIQUE on code.
--    Hibernate auto-named the index (typically `UK<hash>` for `@Column(unique = true)`).
--    Run `SHOW INDEX FROM job_codes;` to find the exact name on your install,
--    then replace below if different.
ALTER TABLE job_codes
    DROP INDEX UKqieucx7kewuth4gfx29w76u40;

-- 5. Add the per-company UNIQUE on (company_id, code).
ALTER TABLE job_codes
    ADD CONSTRAINT uk_job_code_company_code UNIQUE (company_id, code);

-- 6. Foreign key to companies.
ALTER TABLE job_codes
    ADD CONSTRAINT fk_job_codes_company
    FOREIGN KEY (company_id) REFERENCES companies(id);