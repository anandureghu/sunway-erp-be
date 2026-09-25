-- HR Policies → Benefits: one-off benefit grants (annual ticket, bonus, reimbursement)
-- paid through payroll in the chosen pay month.
CREATE TABLE IF NOT EXISTS employee_benefit_grants (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id     BIGINT         NOT NULL,
    employee_id    BIGINT         NOT NULL,
    benefit_type   VARCHAR(30)    NOT NULL,
    amount         DECIMAL(14, 2) NOT NULL,
    pay_month      DATE           NOT NULL,
    description    VARCHAR(500)   NULL,
    document_path  VARCHAR(500)   NULL,
    status         VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    payroll_id     BIGINT         NULL,
    created_by     BIGINT         NULL,
    created_at     DATETIME(6)    NOT NULL,
    CONSTRAINT fk_benefit_grant_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_benefit_grant_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    INDEX idx_benefit_grant_company (company_id, pay_month),
    INDEX idx_benefit_grant_employee (employee_id, benefit_type, pay_month)
);

-- Benefit grants paid in a payroll run (added to gross and net).
ALTER TABLE payroll
    ADD COLUMN benefits_amount DOUBLE NOT NULL DEFAULT 0;
