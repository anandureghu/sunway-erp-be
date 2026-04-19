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
@Table(name = "purchase_order_items")
public class PurchaseOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "item_id")
    private Item item;

    @Column(nullable = false)
    private Integer quantity;

    /** Snapshot of item master cost price at save time (null on legacy rows). */
    @Column(name = "actual_item_price", precision = 18, scale = 2)
    private BigDecimal actualItemPrice;

    /** Optional negotiated / other unit cost override. */
    @Column(name = "other_unit_cost", precision = 18, scale = 2)
    private BigDecimal otherUnitCost;

    /** Applied unit cost used for line_total. */
    @Column(precision = 18, scale = 2, nullable = false)
    private BigDecimal unitCost;

    @Column(precision = 18, scale = 2, nullable = false)
    private BigDecimal lineTotal;
}
