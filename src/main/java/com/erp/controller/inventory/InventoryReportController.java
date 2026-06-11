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
}
