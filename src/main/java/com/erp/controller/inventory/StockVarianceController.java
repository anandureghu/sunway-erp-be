package com.erp.controller.inventory;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.inventory.StockVarianceCreateDTO;
import com.erp.dto.inventory.StockVarianceResponseDTO;
import com.erp.service.inventory.StockVarianceService;
import com.erp.service.security.annotation.RequiresPermission;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory/variances")
public class StockVarianceController {

    private final StockVarianceService service;

    public StockVarianceController(StockVarianceService service) {
        this.service = service;
    }

    @RequiresPermission(module = AppModule.INVENTORY_STOCK, action = {AppAction.CREATE})
    @PostMapping
    public StockVarianceResponseDTO create(@RequestBody StockVarianceCreateDTO dto) {
        return service.create(dto);
    }

    @RequiresPermission(module = AppModule.INVENTORY_STOCK, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/pending")
    public List<StockVarianceResponseDTO> listPending() {
        return service.listPending();
    }

    @RequiresPermission(module = AppModule.INVENTORY_STOCK, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/history")
    public List<StockVarianceResponseDTO> listHistory(
            @RequestParam(value = "archived", required = false, defaultValue = "false") boolean archived
    ) {
        return service.listHistory(archived);
    }

    @RequiresPermission(module = AppModule.INVENTORY_STOCK, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN, AppAction.APPROVE})
    @GetMapping("/can-approve")
    public Map<String, Boolean> canApprove() {
        return Map.of("canApprove", service.canCurrentUserApprove());
    }

    @RequiresPermission(module = AppModule.INVENTORY_STOCK, action = {AppAction.APPROVE})
    @PostMapping("/{id}/approve")
    public StockVarianceResponseDTO approve(@PathVariable("id") Long id) {
        return service.approve(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_STOCK, action = {AppAction.APPROVE})
    @PostMapping("/{id}/reject")
    public StockVarianceResponseDTO reject(@PathVariable("id") Long id) {
        return service.reject(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_STOCK, action = {AppAction.DELETE})
    @PostMapping("/{id}/archive")
    public StockVarianceResponseDTO archive(@PathVariable("id") Long id) {
        return service.archive(id);
    }
}
