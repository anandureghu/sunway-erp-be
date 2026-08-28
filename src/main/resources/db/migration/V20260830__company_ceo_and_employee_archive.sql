-- Company head (CEO / Chairperson) that department managers report to, plus the
-- employee archive lifecycle so inactive/departed staff can be moved out of the
-- active working set.

ALTER TABLE companies
    ADD COLUMN ceo_employee_id BIGINT NULL,
    ADD COLUMN ceo_title VARCHAR(60) NULL,
    ADD CONSTRAINT fk_company_ceo FOREIGN KEY (ceo_employee_id) REFERENCES employees (id);

ALTER TABLE employees
    ADD COLUMN archived BIT(1) NOT NULL DEFAULT b'0',
    ADD COLUMN archived_at DATETIME(6) NULL;
