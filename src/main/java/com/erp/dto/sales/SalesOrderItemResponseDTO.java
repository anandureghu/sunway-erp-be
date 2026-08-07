package com.erp.dto.sales;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesOrderItemResponseDTO {
    private Long id;
    private Long itemId;
    private String itemName;
    private String itemDescription;
    private Integer quantity;
    private Integer returnedQty;
    private BigDecimal unitPrice;
    private BigDecimal lineSubtotal;
    private BigDecimal discountPercent;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private BigDecimal lineTotal;
    private BigDecimal cogsAmount;
    private BigDecimal fifoUnitCost;
    private Long warehouseId;
    private String warehouseName;
}
