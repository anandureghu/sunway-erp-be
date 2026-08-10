package com.erp.domain.sales;

import com.erp.domain.inventory.Item;
import com.erp.domain.inventory.Warehouse;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "sales_order_items")
public class SalesOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "item_id")
    private Item item;

    /** Fulfillment warehouse; legacy rows may be null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    private Integer quantity;

    /** Cumulative quantity returned by the customer against this line. */
    @Column(name = "returned_qty", nullable = false)
    @Builder.Default
    private Integer returnedQty = 0;

    @Column(precision = 18, scale = 2)
    private BigDecimal unitPrice;

    @Column(precision = 18, scale = 2)
    private BigDecimal lineSubtotal;

    @Column(precision = 18, scale = 2)
    private BigDecimal discountPercent;

    @Column(precision = 18, scale = 2)
    private BigDecimal taxRate;

    @Column(precision = 18, scale = 2)
    private BigDecimal taxAmount;

    @Column(precision = 18, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "cogs_amount", precision = 18, scale = 2)
    private BigDecimal cogsAmount;

    @Column(name = "fifo_unit_cost", precision = 18, scale = 2)
    private BigDecimal fifoUnitCost;
}
