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

    @Column(precision = 18, scale = 2, nullable = false)
    private BigDecimal unitCost;

    @Column(precision = 18, scale = 2, nullable = false)
    private BigDecimal lineTotal;
}
