-- Work on a rest day (Fri/Sat) or public holiday is paid on top of salary: the regular
-- part of the shift (up to the standard day) is "rest-day work" at the Friday/holiday
-- rate; anything beyond is overtime (already in overtime_hours / overtime_pay).
ALTER TABLE payroll
    ADD COLUMN rest_day_hours DOUBLE NOT NULL DEFAULT 0,
    ADD COLUMN rest_day_pay DOUBLE NOT NULL DEFAULT 0;
