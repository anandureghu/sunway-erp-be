-- Distinguish manually-entered credit notes from ones automatically generated
-- when an inspection rejects goods on an already-paid purchase order, and
-- trace the auto-generated ones back to the goods receipt that caused them.

DROP PROCEDURE IF EXISTS add_credit_note_source_tracking;
DELIMITER //
CREATE PROCEDURE add_credit_note_source_tracking()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'credit_notes'
           AND COLUMN_NAME = 'source'
    ) THEN
        ALTER TABLE credit_notes
            ADD COLUMN source varchar(32) NOT NULL DEFAULT 'MANUAL' AFTER status,
            ADD COLUMN goods_receipt_id bigint DEFAULT NULL AFTER source;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'credit_notes'
           AND CONSTRAINT_NAME = 'fk_credit_notes_goods_receipt'
    ) THEN
        ALTER TABLE credit_notes
            ADD CONSTRAINT fk_credit_notes_goods_receipt
            FOREIGN KEY (goods_receipt_id) REFERENCES goods_receipts (id);
    END IF;
END //
DELIMITER ;

CALL add_credit_note_source_tracking();
DROP PROCEDURE add_credit_note_source_tracking;
