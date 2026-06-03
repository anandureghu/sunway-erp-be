-- HR policy: loan eligibility & repayment.
--   * loan_min_service_days: days an employee must have worked (since join date)
--     before they may request a loan (e.g. 365 = one year).
--   * loan_max_repayment_months: maximum repayment period allowed for a loan.
-- Enforced on loan application only while loan_policy_enabled is on.
ALTER TABLE companies
    ADD COLUMN loan_policy_enabled bit(1) NOT NULL DEFAULT 0,
    ADD COLUMN loan_min_service_days int NOT NULL DEFAULT 365,
    ADD COLUMN loan_max_repayment_months int NOT NULL DEFAULT 24;
