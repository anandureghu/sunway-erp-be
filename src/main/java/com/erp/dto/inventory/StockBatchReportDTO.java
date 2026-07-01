package com.erp.dto.inventory;

import com.erp.domain.inventory.StockBatchSourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockBatchReportDTO {
    private List<StockBatchResponseDTO> batches;
    private BigDecimal totalValueAtCost;
    private long totalQuantity;
}
