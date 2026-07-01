package com.erp.dto.inventory;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
public class StockVarianceResponseDTO {
    private Long id;
    private String status;
    private String varianceType;
    private String adjustmentMode;
    private Long itemId;
    private String itemName;
    private String itemSku;
    private Long fromWarehouseId;
    private String fromWarehouseName;
    private Long toWarehouseId;
    private String toWarehouseName;
    private Integer quantityBefore;
    private Integer quantityAfter;
    private Integer adjustmentQuantity;
    private Integer transferQuantity;
    private String reason;
    private String notes;
    private LocalDate varianceDate;
    private Long financeTransactionId;
    private Long createdById;
    private String createdByName;
    private Instant createdAt;
    private Long approvedById;
    private String approvedByName;
    private Instant approvedAt;
    private Long rejectedById;
    private String rejectedByName;
    private Instant rejectedAt;
    private boolean archived;
}
