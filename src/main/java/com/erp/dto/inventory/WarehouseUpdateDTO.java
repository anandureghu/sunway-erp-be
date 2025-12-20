package com.erp.dto.inventory;

import lombok.Data;

@Data
public class WarehouseUpdateDTO {
    private String name;
    private String location;
    private String status;
}
