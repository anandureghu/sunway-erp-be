package com.erp.controller.sales;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.common.PageResponse;
import com.erp.dto.sales.PicklistGenerateRequest;
import com.erp.dto.sales.PicklistResponseDTO;
import com.erp.service.sales.PicklistService;
import com.erp.service.security.annotation.RequiresPermission;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/warehouse/picklists")
public class PicklistController {

    private final PicklistService service;

    public PicklistController(PicklistService service) {
        this.service = service;
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.CREATE, AppAction.EDIT})
    @PostMapping("/from-sales-order/{salesOrderId}")
    public PicklistResponseDTO generate(
            @PathVariable("salesOrderId") Long salesOrderId,
            @RequestBody(required = false) PicklistGenerateRequest body
    ) {
        Long warehouseId = body != null ? body.getWarehouseId() : null;
        return service.generate(salesOrderId, warehouseId);
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.EDIT})
    @PostMapping("/{id}/picked")
    public PicklistResponseDTO markPicked(@PathVariable("id") Long id) {
        return service.markPicked(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.EDIT, AppAction.DELETE})
    @PostMapping("/{id}/cancel")
    public PicklistResponseDTO cancel(@PathVariable("id") Long id) {
        return service.cancel(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.DELETE})
    @PostMapping("/{id}/archive")
    public PicklistResponseDTO archive(@PathVariable("id") Long id) {
        return service.archive(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/{id}")
    public PicklistResponseDTO get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping(params = "page")
    public PageResponse<PicklistResponseDTO> listPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "true") boolean excludeDispatched
    ) {
        return service.listPaged(page, size, search, status, excludeDispatched);
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping
    public List<PicklistResponseDTO> list() {
        return service.list();
    }
}
