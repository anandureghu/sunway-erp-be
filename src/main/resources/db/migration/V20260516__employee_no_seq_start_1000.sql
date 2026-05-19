-- =============================================================================
-- Bump the employee_no_seq counter so new employees receive numbers starting at
-- 1000. ALTER TABLE … AUTO_INCREMENT = N is a no-op when the current value is
-- already higher than N, so this migration is safe to re-run and won't disturb
-- existing employees (they keep their assigned numbers).
-- =============================================================================

ALTER TABLE employee_no_seq AUTO_INCREMENT = 1000;
