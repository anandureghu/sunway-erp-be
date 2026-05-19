-- =============================================================================
-- Add employment-classification fields to employee_current_job:
--   * employment_category (Permanent / Contract / Intern / Consultant / Temporary)
--   * employment_type     (Full-time / Part-time)
--   * reporting_manager_id (FK -> employees.id)
--   * contract_start_date  (shown when employment_category != PERMANENT)
--   * contract_end_date    (shown as "contract end date" for non-permanent staff)
--
-- All columns are nullable; legacy rows are treated as Permanent by the UI.
-- =============================================================================

ALTER TABLE employee_current_job
    ADD COLUMN employment_category   VARCHAR(30) NULL,
    ADD COLUMN employment_type       VARCHAR(20) NULL,
    ADD COLUMN reporting_manager_id  BIGINT       NULL,
    ADD COLUMN contract_start_date   DATE         NULL,
    ADD COLUMN contract_end_date     DATE         NULL;

ALTER TABLE employee_current_job
    ADD CONSTRAINT fk_current_job_reporting_manager
        FOREIGN KEY (reporting_manager_id) REFERENCES employees(id);
