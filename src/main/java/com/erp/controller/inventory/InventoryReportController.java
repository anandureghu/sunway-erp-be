package com.erp.controller.inventory;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.inventory.InventoryReportSummaryDTO;
import com.erp.dto.inventory.report.ItemSummaryReportDTO;
import com.erp.dto.inventory.report.StockSummaryReportDTO;
import com.erp.service.inventory.InventoryReportService;
import com.erp.service.inventory.StockSheetReportService;
import com.erp.service.security.annotation.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory/reports")
public class InventoryReportController {

    private final InventoryReportService inventoryReportService;
    /** STOCK_SHEET_REPORTS — remove with Stock Summary / Item Summary pages. */
    private final StockSheetReportService stockSheetReportService;

    public InventoryReportController(
            InventoryReportService inventoryReportService,
            StockSheetReportService stockSheetReportService
    ) {
        this.inventoryReportService = inventoryReportService;
        this.stockSheetReportService = stockSheetReportService;
    }

    /**
     * STOCK_SHEET_REPORTS — Stock Summary sheet (all SKUs).
     * Delete this mapping when the Stock Summary report page is retired.
     */
    @RequiresPermission(module = AppModule.INVENTORY_STOCK, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/stock-summary")
    public StockSummaryReportDTO stockSummary() {
        return stockSheetReportService.stockSummary();
    }

    /**
     * STOCK_SHEET_REPORTS — Item Summary sheet (one SKU).
     * Delete this mapping when the Item Summary report page is retired.
     */
    @RequiresPermission(module = AppModule.INVENTORY_STOCK, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/items/{itemId}/summary")
    public ItemSummaryReportDTO itemSummary(@PathVariable Long itemId) {
        return stockSheetReportService.itemSummary(itemId);
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer limit,
            @RequestParam(defaultValue = "false") boolean archived
    ) {
        int resolvedSize = limit != null ? limit : size;
        return inventoryReportService.buildBatchMovementReport(
                warehouseId, itemId, page, resolvedSize, archived);
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
