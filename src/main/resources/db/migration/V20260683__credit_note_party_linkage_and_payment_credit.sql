-- Credit notes need to be looked up "for this customer" / "for this supplier" independent of
-- any single invoice, so a standing/available credit issued today can be applied to a later
-- payment. Denormalize the party id at creation time (same pattern as Payment.purchaseOrderId)
-- rather than deriving it via invoice -> order -> customer/supplier joins on every read.
-- Payments also need to record how much of their invoice reduction came from an applied credit
-- note rather than cash, for traceability in the payments list.

DROP PROCEDURE IF EXISTS add_credit_note_party_linkage_and_payment_credit;
DELIMITER //
CREATE PROCEDURE add_credit_note_party_linkage_and_payment_credit()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'credit_notes'
           AND COLUMN_NAME = 'customer_id'
    ) THEN
        ALTER TABLE credit_notes
            ADD COLUMN customer_id bigint DEFAULT NULL AFTER goods_receipt_id,
            ADD COLUMN supplier_id bigint DEFAULT NULL AFTER customer_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'credit_notes'
           AND INDEX_NAME = 'idx_credit_notes_customer_id'
    ) THEN
        ALTER TABLE credit_notes ADD INDEX idx_credit_notes_customer_id (customer_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'credit_notes'
           AND INDEX_NAME = 'idx_credit_notes_supplier_id'
    ) THEN
        ALTER TABLE credit_notes ADD INDEX idx_credit_notes_supplier_id (supplier_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'payments'
           AND COLUMN_NAME = 'credit_applied_amount'
    ) THEN
        ALTER TABLE payments
            ADD COLUMN credit_applied_amount decimal(18,2) DEFAULT NULL AFTER amount;
    END IF;
END //
DELIMITER ;

CALL add_credit_note_party_linkage_and_payment_credit();
DROP PROCEDURE add_credit_note_party_linkage_and_payment_credit;
