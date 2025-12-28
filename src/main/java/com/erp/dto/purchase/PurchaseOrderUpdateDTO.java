package com.erp.dto.purchase;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class PurchaseOrderUpdateDTO {
    private Integer quantity;
    private BigDecimal unitCost;
    private LocalDate orderDate;
    private List<PurchaseOrderItemDTO> items;
}
