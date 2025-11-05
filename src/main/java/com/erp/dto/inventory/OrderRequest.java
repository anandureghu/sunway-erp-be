package com.erp.dto.inventory;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderRequest {
    private Long orderId;
    private String orderName;
    private String orderStatus;
    private Long supplierId;
    private String createdBy;
    private String agreement;
    private Instant estimatedDeliveryDate;
    private Instant shipmentDate;
    private Instant orderDate;
    private String notesRemarks;
}
