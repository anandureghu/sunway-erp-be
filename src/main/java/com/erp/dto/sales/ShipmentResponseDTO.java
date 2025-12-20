package com.erp.dto.sales;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class ShipmentResponseDTO {

    private Long id;
    private String shipmentNumber;
    private Long picklistId;
    private Long customerId;
    private String status;
    private String carrierName;
    private String trackingNumber;
    private Instant dispatchedAt;
    private Instant deliveredAt;
    private List<ShipmentItemDTO> items;
}
