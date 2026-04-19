package com.erp.config;

import com.erp.service.inventory.ItemWarehouseStockService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds {@code item_warehouse_stock} from legacy {@code Item} rows on startup.
 */
@Component
@Order(100)
public class ItemWarehouseStockInitializer implements ApplicationRunner {

    private final ItemWarehouseStockService itemWarehouseStockService;

    public ItemWarehouseStockInitializer(ItemWarehouseStockService itemWarehouseStockService) {
        this.itemWarehouseStockService = itemWarehouseStockService;
    }

    @Override
    public void run(ApplicationArguments args) {
        itemWarehouseStockService.seedMissingRowsFromItems();
    }
}
