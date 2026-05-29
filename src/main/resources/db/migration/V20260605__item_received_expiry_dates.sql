ALTER TABLE `items`
    ADD COLUMN `date_received` date DEFAULT NULL AFTER `serial_no`,
    ADD COLUMN `expiry_date` date DEFAULT NULL AFTER `date_received`;
