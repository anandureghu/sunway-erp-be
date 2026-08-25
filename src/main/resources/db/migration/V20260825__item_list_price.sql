-- Catalog list (undiscounted) price for items. Used by Pricing → Item discount
-- so discounts apply from a stable baseline and current discount % can be shown.

DROP PROCEDURE IF EXISTS add_item_list_price;
DELIMITER //
CREATE PROCEDURE add_item_list_price()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'items'
           AND COLUMN_NAME = 'list_price'
    ) THEN
        ALTER TABLE items
            ADD COLUMN list_price decimal(18,2) NULL AFTER selling_price;
    END IF;

    -- Backfill: existing selling price is the best available baseline.
    UPDATE items
       SET list_price = COALESCE(selling_price, unit_sale, list_price)
     WHERE list_price IS NULL
       AND (selling_price IS NOT NULL OR unit_sale IS NOT NULL);
END //
DELIMITER ;

CALL add_item_list_price();
DROP PROCEDURE add_item_list_price;
