package com.erp.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockBatchMovementReportDTO {
    private List<StockBatchMovementResponseDTO> movements;
    private List<StockBatchHistoryPointDTO> receiveTrend;
    private long totalMovements;
    private int page;
    private int size;
    private int totalPages;
    private boolean archived;
}
