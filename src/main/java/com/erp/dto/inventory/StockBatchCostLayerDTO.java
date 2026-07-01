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
public class StockBatchCostLayerDTO {
    private String label;
    private BigDecimal unitCost;
    private int quantity;
    private BigDecimal value;
}
