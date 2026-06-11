-- Support multiple appraisal cycles per year: an employee may now hold one
-- appraisal per (cycle, month) instead of one per (year, month). Each appraisal
-- already references its cycle via config_id, so we re-key uniqueness on it.
-- This is strictly more permissive than the old key (every existing row already
-- maps to exactly one config_id), so no data cleanup is required.

ALTER TABLE `employee_appraisals`
  DROP INDEX `uk_employee_appraisal_month_year`;

ALTER TABLE `employee_appraisals`
  ADD CONSTRAINT `uk_employee_appraisal_config_month`
  UNIQUE (`employee_id`, `config_id`, `month`);
