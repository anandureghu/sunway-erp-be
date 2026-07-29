-- Rename create-status Draft → Quotation for sales orders and purchase requisitions.
-- purchase_requisitions.status is a MySQL ENUM; add QUOTATION before updating rows.

ALTER TABLE purchase_requisitions
    MODIFY COLUMN status ENUM(
        'APPROVED',
        'CONVERTED',
        'DRAFT',
        'QUOTATION',
        'REJECTED',
        'SUBMITTED'
    ) NOT NULL;

UPDATE sales_orders
SET status = 'QUOTATION'
WHERE status = 'DRAFT';

UPDATE purchase_requisitions
SET status = 'QUOTATION'
WHERE status = 'DRAFT';

ALTER TABLE purchase_requisitions
    MODIFY COLUMN status ENUM(
        'APPROVED',
        'CONVERTED',
        'QUOTATION',
        'REJECTED',
        'SUBMITTED'
    ) NOT NULL;
