package com.erp.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DispatchCarrierUpdateDTO {
    private String name;
    private String vehicleNumber;
    private String driverName;
    private String driverPhone;
    private String comments;
    private String status;
}
