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
