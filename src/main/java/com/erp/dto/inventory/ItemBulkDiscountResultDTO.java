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
    private BigDecimal discountPercent;
}
