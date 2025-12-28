package com.erp.domain.purchase;

import com.erp.domain.inventory.Item;
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

    private Integer receivedQty;
    private Integer acceptedQty;
    private Integer rejectedQty;

    private String remarks;
}
