-- PO workflow: Draft → Approved → Release (Confirmed). Add APPROVED to purchase_orders.status ENUM.

ALTER TABLE purchase_orders
    MODIFY COLUMN status ENUM(
        'APPROVED',
        'CANCELLED',
        'CONFIRMED',
        'DRAFT',
        'PARTIALLY_RECEIVED',
        'RECEIVED'
    ) NOT NULL;
