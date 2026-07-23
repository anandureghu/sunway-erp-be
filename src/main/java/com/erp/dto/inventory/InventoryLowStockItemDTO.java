package com.erp.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryLowStockItemDTO {

    private Long itemId;
    private String sku;
    private String name;

    private Long warehouseId;
    private String warehouseName;

    private Integer available;
    private Integer reorderLevel;
}
