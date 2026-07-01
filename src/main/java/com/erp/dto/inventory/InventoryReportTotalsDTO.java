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
public class InventoryReportTotalsDTO {

    /** Distinct items (SKUs) that appear in at least one stock row after filters. */
    private long distinctSkuCount;

    private long totalQuantityOnHand;

    /** Units committed on confirmed sales orders not yet completed. */
    private long totalReserved;

    /** Sum over stock rows of max(0, onHand - reserved). */
    private long totalAvailable;

    /** Sum of quantityOnHand * costPrice per row. */
    private BigDecimal stockValueAtCost;

    /** Sum of quantityOnHand * sellingPrice per row (optional insight). */
    private BigDecimal stockValueAtSelling;

    /** Total units in approved purchase orders not yet fully received. */
    private long totalOnOrder;
}
