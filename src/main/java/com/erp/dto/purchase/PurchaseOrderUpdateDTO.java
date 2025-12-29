package com.erp.dto.purchase;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderUpdateDTO {
    private Integer quantity;
    private BigDecimal unitCost;
    private LocalDate orderDate;
    private List<PurchaseOrderItemDTO> items;
}
