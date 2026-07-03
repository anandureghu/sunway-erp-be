-- Track who inspected and authorized a goods receipt (for certificate signatures).

DROP PROCEDURE IF EXISTS add_goods_receipt_signoff_fields;
DELIMITER //
CREATE PROCEDURE add_goods_receipt_signoff_fields()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'goods_receipts'
           AND COLUMN_NAME = 'inspected_by'
    ) THEN
        ALTER TABLE goods_receipts
            ADD COLUMN inspected_by bigint DEFAULT NULL AFTER received_by,
            ADD COLUMN inspected_at datetime(6) DEFAULT NULL AFTER inspected_by,
            ADD COLUMN authorized_by bigint DEFAULT NULL AFTER inspected_at;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'goods_receipts'
           AND CONSTRAINT_NAME = 'fk_goods_receipts_inspected_by'
    ) THEN
        ALTER TABLE goods_receipts
            ADD CONSTRAINT fk_goods_receipts_inspected_by
            FOREIGN KEY (inspected_by) REFERENCES users (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'goods_receipts'
           AND CONSTRAINT_NAME = 'fk_goods_receipts_authorized_by'
    ) THEN
        ALTER TABLE goods_receipts
            ADD CONSTRAINT fk_goods_receipts_authorized_by
            FOREIGN KEY (authorized_by) REFERENCES users (id);
    END IF;

    -- Backfill existing receipts: use received_by for inspect/authorize when missing.
    UPDATE goods_receipts
    SET
        inspected_by = COALESCE(inspected_by, received_by),
        inspected_at = COALESCE(inspected_at, received_at),
        authorized_by = COALESCE(authorized_by, received_by)
    WHERE received_by IS NOT NULL;
END //
DELIMITER ;

CALL add_goods_receipt_signoff_fields();
DROP PROCEDURE add_goods_receipt_signoff_fields;
