-- Adds an optional middle name to employees. Hibernate runs with ddl-auto=validate,
-- so the column must be created here to match the Employee entity.
ALTER TABLE employees
    ADD COLUMN middle_name VARCHAR(50) NULL AFTER first_name;
