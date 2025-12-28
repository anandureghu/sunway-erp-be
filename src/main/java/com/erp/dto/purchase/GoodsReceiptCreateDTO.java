package com.erp.dto.purchase;

import lombok.Data;

import java.util.List;

@Data
public class GoodsReceiptCreateDTO {

    private Long purchaseOrderId;
    private List<GoodsReceiptItemDTO> items;
}
