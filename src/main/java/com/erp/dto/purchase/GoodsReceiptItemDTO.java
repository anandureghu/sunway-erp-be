package com.erp.dto.purchase;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GoodsReceiptItemDTO {
    private Long itemId;
    private Integer receivedQty;
    private Integer acceptedQty;
    private Integer rejectedQty;
    private String remarks;
}
