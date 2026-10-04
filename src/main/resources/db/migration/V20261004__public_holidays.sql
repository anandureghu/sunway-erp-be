-- HR Settings → Policies → Public Holidays.
-- Company-defined public holidays (one row per holiday, possibly several days, e.g.
-- Eid). A holiday on a working day (Sun–Thu) is a paid day off: employees are not
-- marked absent, it is not deducted from leave, and payroll pays it. Islamic holidays
-- move every year, so dates stay editable and can be flagged as estimated until the
-- official announcement.
CREATE TABLE IF NOT EXISTS company_public_holidays (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    name         VARCHAR(120) NOT NULL,
    start_date   DATE         NOT NULL,
    end_date     DATE         NOT NULL,
    category     VARCHAR(20)  NOT NULL DEFAULT 'NATIONAL',
    recurring    TINYINT(1)   NOT NULL DEFAULT 0,
    confirmed    TINYINT(1)   NOT NULL DEFAULT 1,
    notes        VARCHAR(500) NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    CONSTRAINT fk_public_holiday_company FOREIGN KEY (company_id) REFERENCES companies (id),
    INDEX idx_public_holiday_company_dates (company_id, start_date, end_date)
);

-- Paid public-holiday working days in a payroll run (shown on the payslip / preview).
ALTER TABLE payroll
    ADD COLUMN public_holiday_days DOUBLE NOT NULL DEFAULT 0;
