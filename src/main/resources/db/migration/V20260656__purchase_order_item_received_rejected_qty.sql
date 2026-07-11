-- Track cumulative accepted/rejected quantity per PO line so remaining-to-receive
-- can be computed correctly and a second (partial) receipt against the same line
-- is possible, instead of the PO being force-flipped to RECEIVED on first receipt.

DROP PROCEDURE IF EXISTS add_purchase_order_item_received_rejected_qty;
DELIMITER //
CREATE PROCEDURE add_purchase_order_item_received_rejected_qty()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'purchase_order_items'
           AND COLUMN_NAME = 'received_qty'
    ) THEN
        ALTER TABLE purchase_order_items
            ADD COLUMN received_qty int NOT NULL DEFAULT 0 AFTER quantity,
            ADD COLUMN rejected_qty int NOT NULL DEFAULT 0 AFTER received_qty;
    END IF;

    -- Backfill from linked goods_receipt_items where the link is unambiguous
    -- (populated by the previous migration).
    UPDATE purchase_order_items poi
        JOIN (
            SELECT purchase_order_item_id,
                   SUM(COALESCE(accepted_qty, 0)) AS accepted_sum,
                   SUM(COALESCE(rejected_qty, 0)) AS rejected_sum
              FROM goods_receipt_items
             WHERE purchase_order_item_id IS NOT NULL
             GROUP BY purchase_order_item_id
        ) agg ON agg.purchase_order_item_id = poi.id
    SET poi.received_qty = agg.accepted_sum,
        poi.rejected_qty = agg.rejected_sum
    WHERE poi.received_qty = 0
      AND poi.rejected_qty = 0;

    -- Fallback for lines that couldn't be linked (ambiguous item match) but whose
    -- PO already reached RECEIVED under the old unconditional-RECEIVED behavior:
    -- treat the full ordered quantity as received to preserve prior semantics.
    UPDATE purchase_order_items poi
        JOIN purchase_orders po ON po.id = poi.purchase_order_id
    SET poi.received_qty = poi.quantity
    WHERE poi.received_qty = 0
      AND poi.rejected_qty = 0
      AND po.status = 'RECEIVED';
END //
DELIMITER ;

CALL add_purchase_order_item_received_rejected_qty();
DROP PROCEDURE add_purchase_order_item_received_rejected_qty;
