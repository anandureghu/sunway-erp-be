-- Religion-restricted leave types (e.g. Hajj Leave for Muslim employees only),
-- mirroring the existing gender restriction (e.g. Maternity Leave for females).
ALTER TABLE company_leave_policies
    ADD COLUMN religion_restricted bit(1) NOT NULL DEFAULT 0,
    ADD COLUMN allowed_religion varchar(255) DEFAULT NULL;
