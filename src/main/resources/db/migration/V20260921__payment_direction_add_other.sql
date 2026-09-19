-- Add OTHER to payment_direction enum to support ad-hoc expense payments
-- (rent, reimbursements, utilities) introduced in V20260684__other_payment_fields.sql.
-- Without this, inserting PaymentDirection.OTHER causes "Data truncated" in MySQL.

ALTER TABLE payments
    MODIFY COLUMN payment_direction ENUM('CUSTOMER', 'VENDOR', 'OTHER') DEFAULT NULL;
