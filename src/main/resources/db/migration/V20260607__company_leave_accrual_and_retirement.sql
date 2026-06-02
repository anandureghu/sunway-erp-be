-- Configurable HR policies on the company:
--   * Annual-leave accrual (e.g. 1.5 days per month worked, with a minimum
--     service period before any annual leave can be taken).
--   * Retirement / end-of-service compensation (e.g. one month of basic salary
--     per completed year of service).
ALTER TABLE companies
    ADD COLUMN annual_leave_accrual_enabled bit(1) NOT NULL DEFAULT 0,
    ADD COLUMN annual_leave_accrual_days_per_month decimal(5,2) NOT NULL DEFAULT 1.50,
    ADD COLUMN min_service_months_for_annual_leave int NOT NULL DEFAULT 6,
    ADD COLUMN retirement_compensation_enabled bit(1) NOT NULL DEFAULT 0,
    ADD COLUMN retirement_compensation_months_per_year decimal(5,2) NOT NULL DEFAULT 1.00;
