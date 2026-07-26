package com.erp.dto.dashboard.inventory;

import com.erp.dto.inventory.InventoryLowStockItemDTO;
import com.erp.dto.inventory.InventoryWarehouseBreakdownDTO;
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
public class InventoryDashboardResponseDTO {

    private InventoryDashboardKpisDTO kpis;
    private List<InventoryWarehouseBreakdownDTO> stockByWarehouse;
    private List<InventoryLowStockItemDTO> lowStockItems;
    private InventorySalesPipelineDTO salesPipeline;
    private InventoryPurchasePipelineDTO purchasePipeline;
    private List<InventoryDashboardAlertDTO> alerts;
    private Instant generatedAt;
}
