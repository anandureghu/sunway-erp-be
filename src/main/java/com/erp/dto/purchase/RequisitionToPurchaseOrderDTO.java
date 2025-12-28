package com.erp.dto.purchase;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class RequisitionToPurchaseOrderDTO {

    private Long supplierId;
    private LocalDate orderDate;

    // pricing is decided now
    private List<ItemDTO> items;

    @Data
    public static class ItemDTO {
        private Long itemId;
        private Integer quantity;
        private BigDecimal unitCost;
    }
}
