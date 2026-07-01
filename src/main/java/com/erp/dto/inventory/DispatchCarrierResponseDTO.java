package com.erp.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchCarrierResponseDTO {
    private Long id;
    private String name;
    private String vehicleNumber;
    private String driverName;
    private String driverPhone;
    private String comments;
    private String status;
}
