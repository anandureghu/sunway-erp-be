package com.erp.dto.sales;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentCreateDTO {
    private String carrierName;
    private String trackingNumber;
    private String vehicleNumber;
    private String driverName;
    private String driverPhone;
    private String customerPhone;
    private String estimatedDeliveryDate;
    private String deliveryAddress;
    private String notes;
}
