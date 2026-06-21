package com.erp.dto.purchase;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * No {@code @Builder}: combined with {@code @AllArgsConstructor}, Lombok can omit or
 * privatize the all-args ctor and Jackson deserialization fails with NoSuchMethodError.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequisitionItemDTO {
    private Long itemId;
    /** Item master name (populated on read). */
    private String itemName;
    private Integer requestedQty;
    /** Snapshot of item cost price (populated on read). */
    private BigDecimal actualItemPrice;
    /** Optional other / negotiated unit cost. */
    private BigDecimal otherUnitCost;
    /** Applied estimated unit cost. */
    private BigDecimal estimatedUnitCost;
    /** requestedQty × estimatedUnitCost (populated on read). */
    private BigDecimal estimatedTotal;
    private String remarks;
}
