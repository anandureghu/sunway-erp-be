-- Return-to-office date: the day the employee heads back to work after the
-- leave. Captured on the leave request, distinct from `date_reported` (the date
-- the leave was applied). Nullable — existing rows and optional submissions
-- leave it blank.
ALTER TABLE employee_leaves
    ADD COLUMN return_date date NULL;
