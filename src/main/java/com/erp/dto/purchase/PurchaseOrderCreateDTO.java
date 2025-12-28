package com.erp.dto.purchase;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class PurchaseOrderCreateDTO {
    private Long supplierId;
    private LocalDate orderDate;
    private List<PurchaseOrderItemDTO> items;
}
