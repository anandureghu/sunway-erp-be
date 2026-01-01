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
public class PurchaseRequisitionResponseDTO {

    private Long id;
    private String requisitionNumber;
    private String status;
    private Instant createdAt;
    private Instant approvedAt;
    private List<PurchaseRequisitionItemDTO> items;
}
