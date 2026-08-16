-- Exit interview for departing employees (RESIGNED / TERMINATED / RETIRED).
-- The multi-section questionnaire is stored as JSON in `responses`; key fields are
-- promoted to columns. One row per employee.
CREATE TABLE employee_exit_interview (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    employee_id     BIGINT       NOT NULL,
    company_id      BIGINT       NULL,
    separation_type VARCHAR(40)  NULL,
    last_working_day DATE        NULL,
    primary_reason  VARCHAR(160) NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    responses       LONGTEXT     NULL,
    submitted_at    DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_exit_interview_employee UNIQUE (employee_id),
    CONSTRAINT fk_exit_interview_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_exit_interview_company FOREIGN KEY (company_id) REFERENCES companies (id)
);
