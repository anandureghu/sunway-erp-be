-- "Other Payments" record ad-hoc business expenses (rent, employee/vendor reimbursements,
-- utilities, etc.) that aren't tied to a purchase order or invoice. They reuse the existing
-- payments table/workflow via a new PaymentDirection.OTHER value, plus these two descriptive
-- columns to say what was paid and to whom.

DROP PROCEDURE IF EXISTS add_other_payment_fields;
DELIMITER //
CREATE PROCEDURE add_other_payment_fields()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'payments'
           AND COLUMN_NAME = 'expense_category'
    ) THEN
        ALTER TABLE payments
            ADD COLUMN expense_category varchar(64) DEFAULT NULL AFTER credit_applied_amount,
            ADD COLUMN payee varchar(255) DEFAULT NULL AFTER expense_category;
    END IF;
END //
DELIMITER ;

CALL add_other_payment_fields();
DROP PROCEDURE add_other_payment_fields;
