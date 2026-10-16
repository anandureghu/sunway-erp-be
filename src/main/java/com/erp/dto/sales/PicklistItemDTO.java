package com.erp.dto.sales;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PicklistItemDTO {
    private Long itemId;
    private Integer quantity;
    private String itemName;
    /** Inventory SKU / item code — shown next to the name on picklist detail. */
    private String itemSku;
    private Long warehouseId;
    private String warehouseName;
    /** Available stock at the line warehouse (on-hand − reserved). */
    private Integer availableQuantity;
}
