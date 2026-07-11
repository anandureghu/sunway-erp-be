package com.erp.domain.purchase;

import com.erp.domain.inventory.Item;
import com.erp.domain.inventory.Warehouse;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "goods_receipt_items")
public class GoodsReceiptItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "item_id")
    private Item item;

    /** The exact purchase order line this was received against. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_item_id")
    private PurchaseOrderItem purchaseOrderItem;

    /** Snapshot, at receive time, of the PO line's remaining orderable quantity. */
    @Column(name = "ordered_quantity", nullable = false)
    private Integer orderedQuantity;

    /** Where accepted quantity was posted; null until stock is actually posted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    private Integer receivedQty;
    private Integer acceptedQty;
    private Integer rejectedQty;

    private String remarks;

    @Column(name = "batch_no", length = 100)
    private String batchNo;

    @Column(name = "lot_no", length = 100)
    private String lotNo;

    @Column(name = "unit_cost", precision = 18, scale = 2)
    private BigDecimal unitCost;

    /** Set once this line's accepted quantity has been posted to inventory. */
    @Column(name = "stocked_at")
    private Instant stockedAt;
}
