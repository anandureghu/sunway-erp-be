package com.erp.dto.inventory;

import lombok.Data;

@Data
public class WarehouseCreateDTO {
    private String code;
    private String name;
    private String location;
    private String status;
}
