package com.erp.dto.sales;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicDeliveryTrackingShipmentDTO {
    private String shipmentNumber;
    private String orderNumber;
    private String status;
    private String carrierName;
    private String trackingNumber;
    private String estimatedDeliveryDate;
    private String deliveryAddress;
    private Instant createdAt;
    private Instant deliveredAt;
    private List<PublicDeliveryTrackingItemDTO> items;
    private List<PublicDeliveryTrackingEventDTO> trackingEvents;
}
