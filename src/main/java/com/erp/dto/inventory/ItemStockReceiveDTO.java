package com.erp.dto.inventory;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ItemStockReceiveDTO {

    /** Units to add to on-hand stock (must be positive). */
    private Integer quantityReceived;

    private String receivedDate;
    private String expiryDate;
    private String batchNo;
    private String serialNo;
    private String referenceNo;

    /** If set, must match the item's warehouse. */
    private Long warehouseId;

    private BigDecimal costPrice;
    private BigDecimal unitPrice;
}
