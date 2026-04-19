package com.erp.domain.purchase;

import com.erp.domain.inventory.Item;
import com.erp.domain.inventory.Warehouse;
import jakarta.persistence.*;
import lombok.*;

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

    /** Where accepted quantity was posted; legacy receipts may be null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    private Integer receivedQty;
    private Integer acceptedQty;
    private Integer rejectedQty;

    private String remarks;
}
