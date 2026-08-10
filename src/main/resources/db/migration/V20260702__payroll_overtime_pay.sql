-- Monetary value of overtime, paid on top of the monthly package.
-- OT pay = overtime hours × hourly rate × company day multiplier (default 1.25).
ALTER TABLE payroll
    ADD COLUMN overtime_pay DOUBLE NOT NULL DEFAULT 0 AFTER overtime_hours;
