package com.erp.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemWarehouseStockRowDTO {
    private Long warehouseId;
    private String warehouseName;
    private Integer quantityOnHand;
    private Integer reserved;
    private Integer available;
}
