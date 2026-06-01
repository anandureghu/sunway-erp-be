-- =============================================================================
-- Drop the legacy `grade` column from job_codes.
--
-- V20260513 renames `grade` → `salary_grade` on clean environments. On
-- environments where Hibernate ddl-auto had already created `salary_grade`
-- alongside `grade`, V20260513's CHANGE COLUMN would have failed, leaving
-- both columns present. This migration handles both cases:
--
--   * Sequential (V20260513 ran cleanly): grade no longer exists → no-op.
--   * Hybrid (both columns exist): backfill salary_grade from grade, then drop.
-- =============================================================================

DROP PROCEDURE IF EXISTS drop_job_codes_grade_legacy;
DELIMITER //
CREATE PROCEDURE drop_job_codes_grade_legacy()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME   = 'job_codes'
           AND COLUMN_NAME  = 'grade'
    ) THEN
        -- Backfill only if salary_grade column also exists (hybrid env).
        IF EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME   = 'job_codes'
               AND COLUMN_NAME  = 'salary_grade'
        ) THEN
            UPDATE job_codes
               SET salary_grade = grade
             WHERE (salary_grade IS NULL OR salary_grade = '')
               AND grade IS NOT NULL
               AND grade <> '';
        END IF;

        ALTER TABLE job_codes DROP COLUMN grade;
    END IF;
END //
DELIMITER ;

CALL drop_job_codes_grade_legacy();
DROP PROCEDURE drop_job_codes_grade_legacy;
