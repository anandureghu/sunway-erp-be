package com.erp.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemBulkDiscountResultDTO {
    private int requestedCount;
    private int updatedCount;
    /** Items skipped (missing, no usable list/selling price, wrong company). */
    private int skippedCount;
    /** Items whose discount was limited so selling price stays at/above cost. */
    private int cappedAtCostCount;
    private BigDecimal discountPercent;
}
