-- Loan payment record: one row per repayment of an employee loan — the monthly
-- payroll instalment, a final-settlement recovery, or a manual payment. Shown as the
-- loan's payment record (Month · Paid amount · Total payment).
CREATE TABLE IF NOT EXISTS employee_loan_repayments (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    loan_id        BIGINT       NOT NULL,
    employee_id    BIGINT       NOT NULL,
    company_id     BIGINT       NOT NULL,
    -- First day of the month the repayment belongs to (payroll period end month).
    payment_month  DATE         NOT NULL,
    payment_date   DATE         NOT NULL,
    amount         DOUBLE       NOT NULL,
    -- PAYROLL (monthly instalment), SETTLEMENT (final settlement) or MANUAL.
    source         VARCHAR(20)  NOT NULL DEFAULT 'PAYROLL',
    payroll_id     BIGINT       NULL,
    reference      VARCHAR(100) NULL,
    created_at     DATETIME(6)  NOT NULL,
    CONSTRAINT fk_loan_repayment_loan FOREIGN KEY (loan_id)
        REFERENCES employee_loans (id) ON DELETE CASCADE,
    INDEX idx_loan_repayment_loan (loan_id, payment_month)
);

-- Backfill from past payroll runs. A payroll stores only its total loan deduction,
-- so history can be attributed safely only when the employee has exactly one loan
-- (not pending / rejected) that had started by the end of that pay period.
INSERT INTO employee_loan_repayments
    (loan_id, employee_id, company_id, payment_month, payment_date, amount,
     source, payroll_id, reference, created_at)
SELECT l.id,
       p.employee_id,
       p.company_id,
       DATE_SUB(p.pay_period_end, INTERVAL DAY(p.pay_period_end) - 1 DAY),
       p.pay_date,
       p.loan_deduction,
       CASE WHEN p.final_settlement = 1 THEN 'SETTLEMENT' ELSE 'PAYROLL' END,
       p.id,
       p.payroll_code,
       NOW(6)
FROM payroll p
JOIN employee_loans l
  ON l.employee_id = p.employee_id
 AND UPPER(l.status) IN ('ACTIVE', 'CLOSED')
 AND l.start_date <= p.pay_period_end
WHERE p.loan_deduction > 0
  AND (SELECT COUNT(*) FROM employee_loans x
        WHERE x.employee_id = p.employee_id
          AND UPPER(x.status) IN ('ACTIVE', 'CLOSED')) = 1
  AND NOT EXISTS (SELECT 1 FROM employee_loan_repayments r
                   WHERE r.payroll_id = p.id AND r.loan_id = l.id);
