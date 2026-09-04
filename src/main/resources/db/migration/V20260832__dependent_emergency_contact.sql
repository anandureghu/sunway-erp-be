-- Flag a dependent as the employee's emergency contact.
ALTER TABLE employee_dependents
    ADD COLUMN emergency_contact BIT NOT NULL DEFAULT 0;
