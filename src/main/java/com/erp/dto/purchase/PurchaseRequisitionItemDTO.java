package com.erp.dto.purchase;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PurchaseRequisitionItemDTO {
    private Long itemId;
    private Integer requestedQty;
    private String remarks;
}
