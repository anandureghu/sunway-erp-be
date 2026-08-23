package com.erp.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Keeps discounted unit prices from falling below cost.
 */
public final class DiscountFloor {

    private DiscountFloor() {
    }

    /**
     * Maximum discount % such that {@code unitPrice * (1 - pct/100) >= costPrice}.
     * Returns 0 when unit price is at or below cost; up to 100 when cost is missing/zero.
     */
    public static BigDecimal maxDiscountPercent(BigDecimal unitPrice, BigDecimal costPrice) {
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (costPrice == null || costPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal("100");
        }
        if (unitPrice.compareTo(costPrice) <= 0) {
            return BigDecimal.ZERO;
        }
        // (1 - cost/price) * 100
        BigDecimal ratio = costPrice.divide(unitPrice, 8, RoundingMode.HALF_UP);
        BigDecimal pct = BigDecimal.ONE.subtract(ratio)
                .multiply(new BigDecimal("100"))
                .setScale(4, RoundingMode.HALF_UP);
        if (pct.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (pct.compareTo(new BigDecimal("100")) > 0) {
            return new BigDecimal("100");
        }
        return pct;
    }

    /** Clamp a discounted price so it is never below cost (when cost &gt; 0). */
    public static BigDecimal floorAtCost(BigDecimal discountedPrice, BigDecimal costPrice) {
        if (discountedPrice == null) {
            return costPrice != null ? costPrice : BigDecimal.ZERO;
        }
        if (costPrice == null || costPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return discountedPrice.max(BigDecimal.ZERO);
        }
        return discountedPrice.max(costPrice);
    }

    public static boolean wouldFallBelowCost(
            BigDecimal unitPrice,
            BigDecimal discountPercent,
            BigDecimal costPrice
    ) {
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (costPrice == null || costPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        BigDecimal pct = discountPercent == null ? BigDecimal.ZERO : discountPercent;
        BigDecimal factor = BigDecimal.ONE.subtract(
                pct.divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP));
        BigDecimal after = unitPrice.multiply(factor).setScale(2, RoundingMode.HALF_UP);
        return after.compareTo(costPrice) < 0;
    }
}
