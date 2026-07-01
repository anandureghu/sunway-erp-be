package com.erp.dto.sales;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicDeliveryTrackingEventDTO {
    private String status;
    private String location;
    private String notes;
    private Instant eventAt;
}
