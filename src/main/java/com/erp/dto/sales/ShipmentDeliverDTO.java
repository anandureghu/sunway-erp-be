package com.erp.dto.sales;

import lombok.Data;

@Data
public class ShipmentDeliverDTO {
    /** Customer electronic signature as a data URL or stored image reference. */
    private String customerSignature;
    /** Carrier/customer notes captured at proof of delivery. */
    private String deliveryRemarks;
}
