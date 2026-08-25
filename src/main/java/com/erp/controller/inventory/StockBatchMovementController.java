package com.erp.controller.inventory;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.inventory.StockBatchMovementArchiveRequestDTO;
import com.erp.dto.inventory.StockBatchMovementResponseDTO;
import com.erp.security.context.AuthContext;
import com.erp.service.inventory.StockBatchService;
import com.erp.service.security.annotation.RequiresPermission;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/inventory/batch-movements")
public class StockBatchMovementController {

    private final StockBatchService stockBatchService;
    private final AuthContext auth;

    public StockBatchMovementController(StockBatchService stockBatchService, AuthContext auth) {
        this.stockBatchService = stockBatchService;
        this.auth = auth;
    }

    @RequiresPermission(module = AppModule.INVENTORY_STOCK, action = {AppAction.DELETE})
    @PostMapping("/{id}/archive")
    public StockBatchMovementResponseDTO archive(@PathVariable("id") Long id) {
        return stockBatchService.archiveMovement(auth.getCurrentCompanyId(), id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_STOCK, action = {AppAction.DELETE})
    @PostMapping("/archive")
    public Map<String, Integer> archiveMany(@RequestBody StockBatchMovementArchiveRequestDTO body) {
        int count = stockBatchService.archiveMovements(
                auth.getCurrentCompanyId(),
                body != null ? body.getIds() : null
        );
        return Map.of("archived", count);
    }
}
