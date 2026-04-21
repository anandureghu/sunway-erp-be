package com.erp.dto.sales;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesOrderItemDTO {
    private Long itemId;
    /** Required for new orders: ship from this warehouse. */
    private Long warehouseId;
    private Integer quantity;
    private Double unitPrice;
    private Double discountPercent;
    private Double taxRate;
}
