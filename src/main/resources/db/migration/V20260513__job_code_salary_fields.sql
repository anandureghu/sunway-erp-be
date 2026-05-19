-- =============================================================================
-- Rename job_codes.grade -> job_codes.salary_grade and add salary range fields.
-- The existing `grade` column already holds salary-grade-like values (G1, G2…);
-- this rename makes the naming explicit and adds min/max salary bounds so the
-- HR Settings UI can capture salary ranges per job code.
--
-- Hibernate ddl-auto cannot rename a column; it would silently ADD `salary_grade`
-- alongside the legacy `grade` column. Run this migration BEFORE booting with
-- the renamed entity.
-- =============================================================================

ALTER TABLE job_codes
    CHANGE COLUMN grade salary_grade VARCHAR(50) NOT NULL;

ALTER TABLE job_codes
    ADD COLUMN min_salary DECIMAL(15, 2) NULL,
    ADD COLUMN max_salary DECIMAL(15, 2) NULL;
