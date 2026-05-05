package com.erp.dto.inventory;

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
public class InventoryReportSummaryDTO {

    private InventoryReportTotalsDTO totals;

    private List<InventoryWarehouseBreakdownDTO> byWarehouse;

    private List<InventoryCategoryBreakdownDTO> byCategory;

    private List<InventoryTopStockLineDTO> topStockLinesByValue;

    private long lowStockItemCount;

    private List<InventoryLowStockItemDTO> lowStockItems;

    private Instant generatedAt;
}
