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
    private Integer requestedQty;
    private BigDecimal estimatedUnitCost;
    private String remarks;
}
