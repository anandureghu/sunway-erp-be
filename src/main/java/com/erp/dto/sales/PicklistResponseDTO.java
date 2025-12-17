package com.erp.dto.sales;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class PicklistResponseDTO {

    private Long id;
    private String picklistNumber;
    private Long salesOrderId;
    private String status;
    private Instant createdAt;
    private List<PicklistItemDTO> items;
}
