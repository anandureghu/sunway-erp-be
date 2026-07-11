-- Precise per-line link from a goods receipt item back to the purchase order
-- line it was received against (previously resolved by a fragile item_id scan),
-- a snapshot of the ordered/remaining quantity at receive time, and a marker
-- for when the accepted quantity was actually posted to stock (now a separate
-- step from receiving/inspection).

DROP PROCEDURE IF EXISTS add_goods_receipt_item_po_line_link;
DELIMITER //
CREATE PROCEDURE add_goods_receipt_item_po_line_link()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'goods_receipt_items'
           AND COLUMN_NAME = 'purchase_order_item_id'
    ) THEN
        ALTER TABLE goods_receipt_items
            ADD COLUMN purchase_order_item_id bigint DEFAULT NULL AFTER item_id,
            ADD COLUMN ordered_quantity int NOT NULL DEFAULT 0 AFTER received_qty,
            ADD COLUMN stocked_at datetime(6) DEFAULT NULL AFTER unit_cost;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'goods_receipt_items'
           AND CONSTRAINT_NAME = 'fk_goods_receipt_items_po_item'
    ) THEN
        ALTER TABLE goods_receipt_items
            ADD CONSTRAINT fk_goods_receipt_items_po_item
            FOREIGN KEY (purchase_order_item_id) REFERENCES purchase_order_items (id);
    END IF;

    -- Best-effort backfill: link to the unique matching PO line for the receipt's
    -- own purchase order (skip when the same item appears on more than one line
    -- of that PO - ambiguous, left NULL for manual review).
    UPDATE goods_receipt_items gri
        JOIN goods_receipts gr ON gr.id = gri.goods_receipt_id
        JOIN purchase_order_items poi ON poi.purchase_order_id = gr.purchase_order_id
                                      AND poi.item_id = gri.item_id
    SET gri.purchase_order_item_id = poi.id,
        gri.ordered_quantity = poi.quantity
    WHERE gri.purchase_order_item_id IS NULL
      AND (
          SELECT COUNT(*) FROM purchase_order_items poi2
           WHERE poi2.purchase_order_id = gr.purchase_order_id
             AND poi2.item_id = gri.item_id
      ) = 1;

    -- Legacy receipts posted stock inline at receive time.
    UPDATE goods_receipt_items gri
        JOIN goods_receipts gr ON gr.id = gri.goods_receipt_id
    SET gri.stocked_at = gr.received_at
    WHERE gri.stocked_at IS NULL
      AND gr.received_at IS NOT NULL;
END //
DELIMITER ;

CALL add_goods_receipt_item_po_line_link();
DROP PROCEDURE add_goods_receipt_item_po_line_link;
