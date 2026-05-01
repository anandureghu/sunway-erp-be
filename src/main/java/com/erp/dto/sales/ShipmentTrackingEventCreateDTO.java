package com.erp.dto.sales;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentTrackingEventCreateDTO {
    private String status;
    private String location;
    private String notes;
    private Instant eventAt;
}
