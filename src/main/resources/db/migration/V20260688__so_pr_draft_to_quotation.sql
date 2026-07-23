-- Rename create-status Draft → Quotation for sales orders and purchase requisitions.

UPDATE sales_orders
SET status = 'QUOTATION'
WHERE status = 'DRAFT';

UPDATE purchase_requisitions
SET status = 'QUOTATION'
WHERE status = 'DRAFT';
