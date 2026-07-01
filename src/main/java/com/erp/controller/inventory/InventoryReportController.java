package com.erp.controller.inventory;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.inventory.InventoryReportSummaryDTO;
import com.erp.service.inventory.InventoryReportService;
import com.erp.service.security.annotation.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory/reports")
public class InventoryReportController {

    private final InventoryReportService inventoryReportService;

    public InventoryReportController(InventoryReportService inventoryReportService) {
        this.inventoryReportService = inventoryReportService;
    }

    @RequiresPermission(module = AppModule.INVENTORY_STOCK, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/summary")
    public InventoryReportSummaryDTO summary(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String category
    ) {
        return inventoryReportService.buildSummary(warehouseId, category);
    }

    @RequiresPermission(module = AppModule.INVENTORY_STOCK, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/batches")
    public com.erp.dto.inventory.StockBatchReportDTO batchReport(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long itemId,
            @RequestParam(required = false) String batchNo
    ) {
        return inventoryReportService.buildBatchReport(warehouseId, itemId, batchNo);
    }

    @RequiresPermission(module = AppModule.INVENTORY_STOCK, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/batch-movements")
    public com.erp.dto.inventory.StockBatchMovementReportDTO batchMovements(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long itemId,
            @RequestParam(defaultValue = "150") int limit
    ) {
        return inventoryReportService.buildBatchMovementReport(warehouseId, itemId, limit);
    }

    @RequiresPermission(module = AppModule.INVENTORY_STOCK, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/batch-insights")
    public com.erp.dto.inventory.StockBatchInsightsDTO batchInsights(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long itemId
    ) {
        return inventoryReportService.buildBatchInsights(warehouseId, itemId);
    }
}
