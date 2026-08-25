package com.erp.dto.inventory;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * One-time catalog discount: reduce {@code sellingPrice} (and sync {@code unitSale})
 * from each item's {@code listPrice} baseline by {@code discountPercent}.
 * If {@code listPrice} is missing, the current selling price becomes the baseline.
 */
@Data
public class ItemBulkDiscountRequestDTO {

    /** Items to discount (must belong to the current company). */
    private List<Long> itemIds;

    /**
     * Percent off current selling price (0 exclusive … 100 exclusive recommended;
     * 100 would zero the price).
     */
    private BigDecimal discountPercent;
}
