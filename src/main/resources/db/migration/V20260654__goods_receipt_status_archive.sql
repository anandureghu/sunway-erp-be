-- Split "receiving" from "inspection": goods receipts start PENDING_INSPECTION
-- and only become INSPECTED once quality-check outcomes are confirmed.
-- Also adds archive support, matching the convention used for stock_variances.

DROP PROCEDURE IF EXISTS add_goods_receipt_status_archive;
DELIMITER //
CREATE PROCEDURE add_goods_receipt_status_archive()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'goods_receipts'
           AND COLUMN_NAME = 'status'
    ) THEN
        ALTER TABLE goods_receipts
            ADD COLUMN status varchar(32) NOT NULL DEFAULT 'PENDING_INSPECTION' AFTER purchase_order_id,
            ADD COLUMN archived tinyint(1) NOT NULL DEFAULT 0 AFTER document_pdf_url;
    END IF;

    -- Legacy receipts were inspected atomically at receive time; treat them as INSPECTED.
    UPDATE goods_receipts
    SET status = 'INSPECTED'
    WHERE inspected_at IS NOT NULL
      AND status = 'PENDING_INSPECTION';

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'goods_receipts'
           AND INDEX_NAME = 'idx_goods_receipts_company_status_archived'
    ) THEN
        CREATE INDEX idx_goods_receipts_company_status_archived
            ON goods_receipts (company_id, status, archived);
    END IF;
END //
DELIMITER ;

CALL add_goods_receipt_status_archive();
DROP PROCEDURE add_goods_receipt_status_archive;
