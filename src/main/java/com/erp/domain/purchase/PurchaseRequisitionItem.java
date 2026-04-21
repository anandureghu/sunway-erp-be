package com.erp.domain.purchase;

import com.erp.domain.inventory.Item;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "purchase_requisition_items")
public class PurchaseRequisitionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "item_id")
    private Item item;

    private Integer requestedQty;

    /** Snapshot of item master cost price when the line was saved (null on legacy rows). */
    @Column(name = "actual_item_price", precision = 18, scale = 2)
    private BigDecimal actualItemPrice;

    /** Optional negotiated / other unit cost override. */
    @Column(name = "other_unit_cost", precision = 18, scale = 2)
    private BigDecimal otherUnitCost;

    /** Applied estimated unit cost (line rate used for totals). */
    @Column(precision = 18, scale = 2)
    private BigDecimal estimatedUnitCost;

    private String remarks;
}
