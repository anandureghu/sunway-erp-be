-- Manual monthly overtime entry for companies that do not punch in/out.
-- Without timesheets there is nothing to derive overtime from, so HR keys it here
-- per employee per month; it feeds the attendance report, payroll and payslip.
CREATE TABLE employee_overtime_override (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    employee_id    BIGINT       NOT NULL,
    company_id     BIGINT       NULL,
    period_year    INT          NOT NULL,
    period_month   INT          NOT NULL,
    overtime_hours DOUBLE       NOT NULL DEFAULT 0,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ot_override_emp_period UNIQUE (employee_id, period_year, period_month),
    CONSTRAINT fk_ot_override_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_ot_override_company FOREIGN KEY (company_id) REFERENCES companies (id)
);
