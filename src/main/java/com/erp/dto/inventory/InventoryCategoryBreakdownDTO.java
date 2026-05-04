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
public class InventoryCategoryBreakdownDTO {

    private String category;

    private long skuCount;

    private long onHand;

    private BigDecimal valueAtCost;
}
