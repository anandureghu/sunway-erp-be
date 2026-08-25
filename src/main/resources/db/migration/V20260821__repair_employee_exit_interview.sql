-- Repair: V20260705 was originally max_shift_checkout_grace on prod, then renamed to
-- employee_exit_interview in the repo. Flyway already recorded version 20260705 as
-- success, so the exit-interview CREATE never ran and Hibernate validate fails with
-- missing table [employee_exit_interview]. Create it if absent.

CREATE TABLE IF NOT EXISTS employee_exit_interview (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    employee_id      BIGINT       NOT NULL,
    company_id       BIGINT       NULL,
    separation_type  VARCHAR(40)  NULL,
    last_working_day DATE         NULL,
    primary_reason   VARCHAR(160) NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    responses        LONGTEXT     NULL,
    submitted_at     DATETIME(6)  NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_exit_interview_employee UNIQUE (employee_id),
    CONSTRAINT fk_exit_interview_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_exit_interview_company FOREIGN KEY (company_id) REFERENCES companies (id)
);
