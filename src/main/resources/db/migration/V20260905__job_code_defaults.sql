-- Job-code defaults that are copied onto an employee's current job when the code is
-- assigned: department, division, employment classification and work location.
ALTER TABLE job_codes
    ADD COLUMN department_id        BIGINT       NULL,
    ADD COLUMN division_id          BIGINT       NULL,
    ADD COLUMN employment_category  VARCHAR(30)  NULL,
    ADD COLUMN employment_type      VARCHAR(30)  NULL,
    ADD COLUMN work_location        VARCHAR(30)  NULL,
    ADD COLUMN work_city            VARCHAR(100) NULL,
    ADD COLUMN work_country         VARCHAR(100) NULL;
