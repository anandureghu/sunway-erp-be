package com.erp.service.purchase;

import com.erp.domain.inventory.Item;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Resolves purchase line rates: snapshot cost from item master, optional "other" override,
 * and the applied unit cost used for totals.
 */
public final class PurchaseLinePricing {

    private PurchaseLinePricing() {}

    public record Resolved(
            BigDecimal actualItemPrice,
            BigDecimal otherUnitCost,
            BigDecimal appliedUnitCost
    ) {}

    /** Snapshot from item master; null cost price becomes zero for storage, validation may still fail. */
    public static BigDecimal actualItemPriceFromItem(Item item) {
        Objects.requireNonNull(item, "item");
        if (item.getCostPrice() == null) {
            return BigDecimal.ZERO;
        }
        return item.getCostPrice();
    }

    /**
     * PR line: applied = other if set, else estimated from client if set, else actual snapshot.
     */
    public static Resolved resolveRequisitionLine(
            Item item,
            BigDecimal otherUnitCost,
            BigDecimal estimatedUnitCostFromClient
    ) {
        BigDecimal actual = actualItemPriceFromItem(item);
        BigDecimal applied;
        if (otherUnitCost != null) {
            applied = otherUnitCost;
        } else if (estimatedUnitCostFromClient != null) {
            applied = estimatedUnitCostFromClient;
        } else {
            applied = actual;
        }
        validateAppliedPositive(item.getId(), applied);
        return new Resolved(actual, otherUnitCost, applied);
    }

    /**
     * PO line: applied = other if set, else unit cost from client if set, else actual snapshot.
     */
    public static Resolved resolveOrderLine(
            Item item,
            BigDecimal otherUnitCost,
            BigDecimal unitCostFromClient
    ) {
        BigDecimal actual = actualItemPriceFromItem(item);
        BigDecimal applied;
        if (otherUnitCost != null) {
            applied = otherUnitCost;
        } else if (unitCostFromClient != null) {
            applied = unitCostFromClient;
        } else {
            applied = actual;
        }
        validateAppliedPositive(item.getId(), applied);
        return new Resolved(actual, otherUnitCost, applied);
    }

    private static void validateAppliedPositive(Long itemId, BigDecimal applied) {
        if (applied.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException(
                    "Applied unit cost must be positive for item " + itemId
                            + ". Set item cost price, or provide an other cost / line estimate.");
        }
    }
}
