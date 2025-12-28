package com.erp.dto.purchase;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PurchaseOrderItemDTO {
    private Long itemId;
    private Integer quantity;
    private BigDecimal unitCost;
    private BigDecimal lineTotal;
}
