package com.erp.dto.inventory;

import lombok.Data;

@Data
public class WarehouseUpdateDTO {
    private String name;
    private String status;
    private String street;
    private String city;
    private String country;
    private String pin;
    private String phone;
    private String contactPersonName;
    private Long manager;
}
