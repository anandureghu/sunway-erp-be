package com.erp.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryTopStockLineDTO {

    private Long itemId;
    private String sku;
    private String name;

    private Long warehouseId;
    private String warehouseName;

    private int quantityOnHand;

    private BigDecimal valueAtCost;
}
