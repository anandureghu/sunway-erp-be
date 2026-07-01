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
public class ShipmentResponseDTO {

    private Long id;
    private String shipmentNumber;
    private Long picklistId;
    private Long customerId;
    private String status;
    private String carrierName;
    private String trackingNumber;
    private String vehicleNumber;
    private String driverName;
    private String driverPhone;
    private String customerPhone;
    private String estimatedDeliveryDate;
    private String deliveryAddress;
    private String notes;
    private Instant createdAt;
    private Instant dispatchedAt;
    private Instant inTransitAt;
    private Instant outForDeliveryAt;
    private Instant deliveredAt;
    private Instant failedDeliveryAt;
    private List<ShipmentItemDTO> items;
    private List<ShipmentTrackingEventDTO> trackingEvents;
}
