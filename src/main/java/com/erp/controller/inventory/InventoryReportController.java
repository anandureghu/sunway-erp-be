package com.erp.controller.inventory;

import com.erp.dto.inventory.InventoryReportSummaryDTO;
import com.erp.service.inventory.InventoryReportService;
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

    /**
     * Point-in-time inventory snapshot for the current company.
     *
     * @param warehouseId optional — limit to one warehouse’s stock rows
     * @param category    optional — exact match on item category string
     */
    @GetMapping("/summary")
    public InventoryReportSummaryDTO summary(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String category
    ) {
        return inventoryReportService.buildSummary(warehouseId, category);
    }
}
