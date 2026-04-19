package com.erp.domain.inventory;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "item_warehouse_stock",
        uniqueConstraints = @UniqueConstraint(columnNames = {"item_id", "warehouse_id"})
)
public class ItemWarehouseStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private Item item;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    /** Physical quantity at this location. */
    @Column(name = "quantity_on_hand", nullable = false)
    private Integer quantityOnHand;

    /** Reserved for orders not yet fulfilled. */
    @Column(nullable = false)
    private Integer reserved;

    public int available() {
        int on = quantityOnHand == null ? 0 : quantityOnHand;
        int res = reserved == null ? 0 : reserved;
        return Math.max(0, on - res);
    }
}
