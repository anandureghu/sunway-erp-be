-- payment_code (e.g. "VAP-1008") was globally unique across all companies,
-- but the sequence generator resets per company, so two companies can
-- legitimately land on the same code. Scope the uniqueness to the company.

DROP PROCEDURE IF EXISTS scope_payment_code_uniqueness_to_company;
DELIMITER //
CREATE PROCEDURE scope_payment_code_uniqueness_to_company()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'payments'
           AND INDEX_NAME = 'UKn939tpje3kshdfk6pbi7ewwj7'
    ) THEN
        ALTER TABLE payments DROP INDEX UKn939tpje3kshdfk6pbi7ewwj7;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'payments'
           AND INDEX_NAME = 'uk_payments_company_payment_code'
    ) THEN
        ALTER TABLE payments
            ADD CONSTRAINT uk_payments_company_payment_code
            UNIQUE (company_id, payment_code);
    END IF;
END //
DELIMITER ;

CALL scope_payment_code_uniqueness_to_company();
DROP PROCEDURE scope_payment_code_uniqueness_to_company;
