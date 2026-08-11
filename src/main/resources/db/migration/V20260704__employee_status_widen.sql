-- The Employee.status entity is @Enumerated(EnumType.STRING) with 7 values
-- (ACTIVE, INACTIVE, ON_LEAVE, UNDER_PROBATION, RESIGNED, TERMINATED, RETIRED),
-- but the column was a MySQL ENUM listing only the first three. Any insert/update
-- with a newer status (e.g. a probation new-hire → UNDER_PROBATION) failed with
-- "Data truncated for column 'status'". Convert to VARCHAR so the column holds every
-- enum name and never needs DDL again when a status is added.
ALTER TABLE employees
    MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE';
