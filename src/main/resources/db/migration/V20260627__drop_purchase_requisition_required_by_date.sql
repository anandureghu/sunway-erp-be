-- required_by_date duplicated required_delivery_date; merge then drop.
UPDATE purchase_requisitions
SET required_delivery_date = required_by_date
WHERE required_delivery_date IS NULL
  AND required_by_date IS NOT NULL;

ALTER TABLE purchase_requisitions
    DROP COLUMN required_by_date;
