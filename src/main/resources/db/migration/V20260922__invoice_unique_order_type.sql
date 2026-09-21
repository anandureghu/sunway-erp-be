-- Prevent duplicate invoices for the same order+type (caused by double-click race condition)
-- First remove any existing duplicates, keeping the one with the lowest id
DELETE i1 FROM invoices i1
INNER JOIN invoices i2
    ON i1.order_id = i2.order_id
    AND i1.type    = i2.type
    AND i1.id      > i2.id
WHERE i1.order_id IS NOT NULL;

ALTER TABLE invoices
    ADD CONSTRAINT uq_invoices_order_type UNIQUE (order_id, type);
