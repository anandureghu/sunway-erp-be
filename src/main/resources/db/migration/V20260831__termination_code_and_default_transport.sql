-- Termination reason code on the employee (set when status becomes TERMINATED),
-- and a company default transportation allowance (Statutory Compensation).
ALTER TABLE employees
    ADD COLUMN termination_code VARCHAR(40) NULL;

ALTER TABLE companies
    ADD COLUMN default_transportation_allowance DECIMAL(12,2) NOT NULL DEFAULT 0.00;
