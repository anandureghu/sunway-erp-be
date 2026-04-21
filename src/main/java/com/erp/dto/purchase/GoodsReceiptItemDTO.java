package com.erp.dto.purchase;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodsReceiptItemDTO {
    private Long itemId;
    private Long warehouseId;
    private Integer receivedQty;
    private Integer acceptedQty;
    private Integer rejectedQty;
    private String remarks;
}
