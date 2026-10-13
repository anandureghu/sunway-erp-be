package com.erp.controller.purchase;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.common.PageResponse;
import com.erp.dto.purchase.PurchaseOrderAssignSupplierDTO;
import com.erp.dto.purchase.PurchaseOrderCreateDTO;
import com.erp.dto.purchase.PurchaseOrderPostingPreviewDTO;
import com.erp.dto.purchase.PurchaseOrderResponseDTO;
import com.erp.dto.purchase.PurchaseOrderUpdateDTO;
import com.erp.service.purchase.PurchaseOrderService;
import com.erp.service.security.annotation.RequiresPermission;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/purchase/orders")
public class PurchaseOrderController {

    private final PurchaseOrderService service;

    public PurchaseOrderController(PurchaseOrderService service) {
        this.service = service;
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.CREATE})
    @PostMapping
    public PurchaseOrderResponseDTO create(@RequestBody PurchaseOrderCreateDTO dto) {
        return service.create(dto);
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.EDIT})
    @PostMapping("/{id}/supplier")
    public PurchaseOrderResponseDTO assignSupplier(
            @PathVariable("id") Long id,
            @RequestBody PurchaseOrderAssignSupplierDTO dto
    ) {
        return service.assignSupplier(id, dto);
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.APPROVE, AppAction.EDIT})
    @PostMapping("/{id}/approve")
    public PurchaseOrderResponseDTO approve(@PathVariable("id") Long id) {
        return service.approve(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.APPROVE, AppAction.EDIT})
    @PostMapping("/{id}/confirm")
    public PurchaseOrderResponseDTO confirm(@PathVariable("id") Long id) {
        return service.confirm(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/{id}")
    public PurchaseOrderResponseDTO get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/{id}/posting-preview")
    public PurchaseOrderPostingPreviewDTO postingPreview(
            @PathVariable("id") Long id,
            @RequestParam("action") String action
    ) {
        return service.getPostingPreview(id, action);
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping(params = "page")
    public PageResponse<PurchaseOrderResponseDTO> listPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        return service.listPaged(page, size, search, tab, status, paymentStatus, dateFrom, dateTo);
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping
    public List<PurchaseOrderResponseDTO> list() {
        return service.list();
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.EDIT})
    @PutMapping("/{id}")
    public PurchaseOrderResponseDTO update(
            @PathVariable("id") Long id,
            @RequestBody PurchaseOrderUpdateDTO dto
    ) {
        return service.update(id, dto);
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.EDIT, AppAction.DELETE})
    @PostMapping("/{id}/cancel")
    public PurchaseOrderResponseDTO cancel(@PathVariable("id") Long id) {
        return service.cancel(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.DELETE})
    @PostMapping("/{id}/archive")
    public PurchaseOrderResponseDTO archive(@PathVariable("id") Long id) {
        return service.archive(id);
    }
}
