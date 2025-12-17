package com.erp.dto.sales;

import lombok.Data;

@Data
public class ShipmentCreateDTO {
    private String carrierName;
    private String trackingNumber;
}
