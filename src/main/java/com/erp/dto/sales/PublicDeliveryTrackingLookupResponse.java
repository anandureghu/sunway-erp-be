package com.erp.dto.sales;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicDeliveryTrackingLookupResponse {
    private PublicDeliveryTrackingCompanyDTO company;
    private List<PublicDeliveryTrackingShipmentDTO> deliveries;
}
