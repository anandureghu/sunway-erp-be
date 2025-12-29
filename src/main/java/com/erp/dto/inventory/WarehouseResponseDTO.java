package com.erp.dto.inventory;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WarehouseResponseDTO {
    private Long id;
    private String code;
    private String name;
    private String status;

    private String street;
    private String city;
    private String country;
    private String pin;
    private String phone;
    private String contactPersonName;
    private Long managerId;
    private String managerName;
}
