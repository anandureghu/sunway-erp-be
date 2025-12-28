package com.erp.dto.purchase;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class GoodsReceiptResponseDTO {

    private Long id;
    private Long purchaseOrderId;
    private Instant receivedAt;
    private List<GoodsReceiptItemDTO> items;
}
