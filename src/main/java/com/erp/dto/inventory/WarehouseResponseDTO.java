package com.erp.dto.inventory;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WarehouseResponseDTO {
    private Long id;
    private String code;
    private String name;
    private String location;
    private String status;
}
