CREATE TABLE IF NOT EXISTS `stock_batches` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `company_id` bigint NOT NULL,
    `item_id` bigint NOT NULL,
    `warehouse_id` bigint NOT NULL,
    `batch_no` varchar(100) NOT NULL,
    `quantity_on_hand` int NOT NULL DEFAULT 0,
    `unit_cost` decimal(18,2) NOT NULL DEFAULT 0.00,
    `received_at` date NOT NULL,
    `expiry_date` date DEFAULT NULL,
    `source_type` varchar(30) NOT NULL,
    `source_id` bigint DEFAULT NULL,
    `created_at` datetime(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_stock_batches_layer` (`company_id`, `item_id`, `warehouse_id`, `batch_no`, `unit_cost`),
    KEY `idx_stock_batches_fifo` (`item_id`, `warehouse_id`, `received_at`, `id`),
    KEY `idx_stock_batches_company` (`company_id`),
    CONSTRAINT `fk_stock_batches_company` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`),
    CONSTRAINT `fk_stock_batches_item` FOREIGN KEY (`item_id`) REFERENCES `items` (`id`),
    CONSTRAINT `fk_stock_batches_warehouse` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `stock_batch_movements` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `stock_batch_id` bigint NOT NULL,
    `movement_type` varchar(30) NOT NULL,
    `quantity` int NOT NULL,
    `unit_cost` decimal(18,2) NOT NULL,
    `reference_type` varchar(50) DEFAULT NULL,
    `reference_id` bigint DEFAULT NULL,
    `created_at` datetime(6) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_stock_batch_movements_batch` (`stock_batch_id`),
    KEY `idx_stock_batch_movements_ref` (`reference_type`, `reference_id`),
    CONSTRAINT `fk_stock_batch_movements_batch` FOREIGN KEY (`stock_batch_id`) REFERENCES `stock_batches` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `goods_receipt_items`
    ADD COLUMN `batch_no` varchar(100) DEFAULT NULL,
    ADD COLUMN `lot_no` varchar(100) DEFAULT NULL,
    ADD COLUMN `unit_cost` decimal(18,2) DEFAULT NULL;

ALTER TABLE `sales_order_items`
    ADD COLUMN `cogs_amount` decimal(18,2) DEFAULT NULL,
    ADD COLUMN `fifo_unit_cost` decimal(18,2) DEFAULT NULL;

INSERT INTO `stock_batches` (
    `company_id`, `item_id`, `warehouse_id`, `batch_no`,
    `quantity_on_hand`, `unit_cost`, `received_at`, `source_type`, `created_at`
)
SELECT
    i.company_id,
    iws.item_id,
    iws.warehouse_id,
    CONCAT('LEGACY-', iws.item_id),
    iws.quantity_on_hand,
    COALESCE(i.cost_price, 0),
    COALESCE(i.date_received, CURDATE()),
    'MIGRATION',
    NOW(6)
FROM `item_warehouse_stock` iws
JOIN `items` i ON i.id = iws.item_id
WHERE iws.quantity_on_hand > 0
  AND NOT EXISTS (
      SELECT 1 FROM `stock_batches` sb
      WHERE sb.item_id = iws.item_id AND sb.warehouse_id = iws.warehouse_id
  );
