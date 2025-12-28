package com.erp.dto.purchase;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class PurchaseOrderResponseDTO {
    private Long id;
    private String orderNumber;
    private Long supplierId;
    private String supplierName;
    private LocalDate orderDate;
    private String status;
    private BigDecimal totalAmount;
    private List<PurchaseOrderItemDTO> items;
    private String createdAt;
    private Long createdById;
    private String createdByName;
}
