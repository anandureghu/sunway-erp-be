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
public class InventoryWarehouseBreakdownDTO {

    private Long warehouseId;
    private String warehouseName;

    private long onHand;
    private long reserved;
    private long available;

    private BigDecimal valueAtCost;
}
