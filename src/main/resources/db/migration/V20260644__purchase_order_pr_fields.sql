-- Carry PR required delivery date and requester onto purchase orders.

DROP PROCEDURE IF EXISTS add_purchase_order_pr_fields;
DELIMITER //
CREATE PROCEDURE add_purchase_order_pr_fields()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'purchase_orders'
           AND COLUMN_NAME = 'required_delivery_date'
    ) THEN
        ALTER TABLE purchase_orders
            ADD COLUMN required_delivery_date date DEFAULT NULL AFTER order_date;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'purchase_orders'
           AND COLUMN_NAME = 'requested_by'
    ) THEN
        ALTER TABLE purchase_orders
            ADD COLUMN requested_by bigint DEFAULT NULL AFTER created_by;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'purchase_orders'
           AND CONSTRAINT_NAME = 'fk_purchase_orders_requested_by'
    ) THEN
        ALTER TABLE purchase_orders
            ADD CONSTRAINT fk_purchase_orders_requested_by
            FOREIGN KEY (requested_by) REFERENCES users (id);
    END IF;

    -- Backfill from source requisition for existing POs.
    UPDATE purchase_orders po
    JOIN purchase_requisitions pr ON pr.id = po.source_requisition_id
    SET
        po.required_delivery_date = COALESCE(po.required_delivery_date, pr.required_delivery_date),
        po.requested_by = COALESCE(po.requested_by, pr.requested_by)
    WHERE po.source_requisition_id IS NOT NULL;
END //
DELIMITER ;

CALL add_purchase_order_pr_fields();
DROP PROCEDURE add_purchase_order_pr_fields;
