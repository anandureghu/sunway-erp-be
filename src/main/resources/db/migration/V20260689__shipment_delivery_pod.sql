ALTER TABLE `shipments`
    ADD COLUMN `customer_signature` MEDIUMTEXT NULL AFTER `notes`,
    ADD COLUMN `delivery_remarks` VARCHAR(2000) NULL AFTER `customer_signature`;
