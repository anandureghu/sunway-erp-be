package com.erp.dto.purchase;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodsReceiptResponseDTO {

    private Long id;
    private Long purchaseOrderId;
    private String status;
    private boolean archived;
    private Instant receivedAt;
    private Long receivedById;
    private String receivedByName;
    private Long inspectedById;
    private String inspectedByName;
    private Instant inspectedAt;
    private Long authorizedById;
    private String authorizedByName;
    private String documentPdfUrl;
    private List<GoodsReceiptItemDTO> items;
}
