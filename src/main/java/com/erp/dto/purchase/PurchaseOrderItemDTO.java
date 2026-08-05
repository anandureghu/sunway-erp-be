package com.erp.dto.purchase;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderItemDTO {
    private Long itemId;
    /** Resolved item label for display (e.g. invoice PDF). */
    private String itemName;
    /** Optional detail text from catalog item. */
    private String itemDescription;
    private Integer quantity;
    /** Snapshot of item cost price at save time. */
    private BigDecimal actualItemPrice;
    /** Optional other / negotiated unit cost. */
    private BigDecimal otherUnitCost;
    /** Applied unit cost (used for line total). */
    private BigDecimal unitCost;
    /**
     * Unit price shown on generated invoices ({@code unitCost} snapshot); aligns with invoice template
     * field name used for sales lines.
     */
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
    /** Cumulative accepted quantity posted against this PO line. */
    private Integer receivedQty;
    /** Cumulative rejected quantity against this PO line. */
    private Integer rejectedQty;
}
