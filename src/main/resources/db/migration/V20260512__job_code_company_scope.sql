-- =============================================================================
-- Job codes: convert from global to company-scoped.
--
-- Wrapped in a stored procedure so this migration is idempotent. On a fresh
-- environment (running on top of V1) none of these changes exist yet and all
-- steps apply cleanly. On an existing environment where Hibernate ddl-auto
-- already added company_id, the conditional guards skip duplicate work.
-- =============================================================================

DROP PROCEDURE IF EXISTS migrate_job_codes_company_scope;
DELIMITER //
CREATE PROCEDURE migrate_job_codes_company_scope()
BEGIN
    -- 1. Add company_id as nullable if it does not exist yet.
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME   = 'job_codes'
           AND COLUMN_NAME  = 'company_id'
    ) THEN
        ALTER TABLE job_codes ADD COLUMN company_id BIGINT NULL;
    END IF;

    -- 2. Backfill any rows without an owner.
    UPDATE job_codes
       SET company_id = (SELECT MIN(id) FROM companies)
     WHERE company_id IS NULL;

    -- 3. Enforce NOT NULL.
    ALTER TABLE job_codes MODIFY COLUMN company_id BIGINT NOT NULL;

    -- 4. Drop the old global UNIQUE on code if it still exists.
    IF EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME   = 'job_codes'
           AND INDEX_NAME   = 'UKqieucx7kewuth4gfx29w76u40'
    ) THEN
        ALTER TABLE job_codes DROP INDEX UKqieucx7kewuth4gfx29w76u40;
    END IF;

    -- 5. Add per-company UNIQUE if not present.
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME   = 'job_codes'
           AND INDEX_NAME   = 'uk_job_code_company_code'
    ) THEN
        ALTER TABLE job_codes
            ADD CONSTRAINT uk_job_code_company_code UNIQUE (company_id, code);
    END IF;

    -- 6. Add FK to companies if no FK from job_codes.company_id already exists.
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.KEY_COLUMN_USAGE
         WHERE TABLE_SCHEMA         = DATABASE()
           AND TABLE_NAME           = 'job_codes'
           AND COLUMN_NAME          = 'company_id'
           AND REFERENCED_TABLE_NAME = 'companies'
    ) THEN
        ALTER TABLE job_codes
            ADD CONSTRAINT fk_job_codes_company
            FOREIGN KEY (company_id) REFERENCES companies(id);
    END IF;
END //
DELIMITER ;

CALL migrate_job_codes_company_scope();
DROP PROCEDURE migrate_job_codes_company_scope;
