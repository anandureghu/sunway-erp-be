package com.erp.dto.inventory;

import lombok.Data;

@Data
public class ItemStockAdjustDTO {

    /**
     * Delta applied to current quantity (positive or negative).
     * Ignored when {@code newQuantity} is set.
     */
    private Integer adjustmentQuantity;

    /**
     * When set, quantity becomes this value (absolute on-hand).
     */
    private Integer newQuantity;

    private String reason;
    private String adjustmentType;
    private String adjustmentDate;
    private String notes;

    /** If set, must match the item's warehouse. */
    private Long warehouseId;
}
