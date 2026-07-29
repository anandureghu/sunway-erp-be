-- Company-level attendance policy:
--   * standard_working_hours_per_day — the expected length of a full working day.
--     Drives "full day worked" thresholds and payroll worked-day math. Defaults to 6h.
--   * require_check_in — when 0, the organization does not punch in/out; every active
--     employee is auto-marked present for the standard day (see AttendanceReportService).
--     Defaults to 1 (existing behaviour: check-in required).
ALTER TABLE `companies`
  ADD COLUMN `standard_working_hours_per_day` DECIMAL(4,2) NOT NULL DEFAULT 6.00,
  ADD COLUMN `require_check_in` TINYINT(1) NOT NULL DEFAULT 1;

-- Audit for the nightly auto-checkout job that closes sessions an employee forgot to
-- check out of: `auto_checked_out` flags the row, `note` carries the human explanation.
ALTER TABLE `employee_timesheets`
  ADD COLUMN `auto_checked_out` TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN `note` VARCHAR(255) NULL;
