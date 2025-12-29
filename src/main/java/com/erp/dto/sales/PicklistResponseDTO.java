package com.erp.dto.sales;

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
public class PicklistResponseDTO {

    private Long id;
    private String picklistNumber;
    private Long salesOrderId;
    private String status;
    private Instant createdAt;
    private List<PicklistItemDTO> items;
}
