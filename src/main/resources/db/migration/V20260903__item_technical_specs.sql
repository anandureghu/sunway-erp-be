-- Technical specification fields and negative-stock policy per item.

ALTER TABLE items
    ADD COLUMN manufacturer_part_number varchar(150) NULL AFTER brand,
    ADD COLUMN model varchar(150) NULL AFTER manufacturer_part_number,
    ADD COLUMN negative_stock_permitted tinyint(1) NOT NULL DEFAULT 0 AFTER model;
