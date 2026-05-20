-- =============================================================================
-- Drop the legacy `grade` column from job_codes.
--
-- V20260513 intended to rename `grade` -> `salary_grade`, but on environments
-- where Hibernate ddl-auto had already created `salary_grade` alongside
-- `grade`, the rename ended up leaving BOTH columns. The orphan `grade` is
-- still declared NOT NULL with no default, so every fresh INSERT from the
-- entity (which only writes `salary_grade`) fails with:
--   "Field 'grade' doesn't have a default value"
--
-- Backfill `salary_grade` from `grade` for any rows where the rename never
-- carried the value over (we observed rows with grade='G1' but
-- salary_grade=''), then drop the legacy column.
-- =============================================================================

UPDATE job_codes
SET salary_grade = grade
WHERE (salary_grade IS NULL OR salary_grade = '')
  AND grade IS NOT NULL
  AND grade <> '';

ALTER TABLE job_codes
    DROP COLUMN grade;
