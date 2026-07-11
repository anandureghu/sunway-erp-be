package com.erp.controller.purchase;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.purchase.GoodsReceiptCreateDTO;
import com.erp.dto.purchase.GoodsReceiptResponseDTO;
import com.erp.dto.purchase.InspectionConfirmDTO;
import com.erp.dto.purchase.StockPostingDTO;
import com.erp.service.purchase.GoodsReceiptService;
import com.erp.service.security.annotation.RequiresPermission;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/purchase/receipts")
public class GoodsReceiptController {

    private final GoodsReceiptService service;

    public GoodsReceiptController(GoodsReceiptService service) {
        this.service = service;
    }

    @RequiresPermission(module = AppModule.INVENTORY_RECEIPT, action = {AppAction.CREATE})
    @PostMapping
    public GoodsReceiptResponseDTO receive(@RequestBody GoodsReceiptCreateDTO dto) {
        return service.receive(dto);
    }

    @RequiresPermission(module = AppModule.INVENTORY_RECEIPT, action = {AppAction.APPROVE})
    @PostMapping("/{id}/confirm-inspection")
    public GoodsReceiptResponseDTO confirmInspection(
            @PathVariable("id") Long id, @RequestBody InspectionConfirmDTO dto) {
        return service.confirmInspection(id, dto);
    }

    @RequiresPermission(module = AppModule.INVENTORY_RECEIPT, action = {AppAction.CREATE})
    @PostMapping("/{id}/post-stock")
    public GoodsReceiptResponseDTO postStock(
            @PathVariable("id") Long id, @RequestBody StockPostingDTO dto) {
        return service.postItemsToStock(id, dto);
    }

    @RequiresPermission(module = AppModule.INVENTORY_RECEIPT, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/awaiting-stock")
    public List<GoodsReceiptResponseDTO> listAwaitingStock() {
        return service.listAwaitingStock();
    }

    @RequiresPermission(module = AppModule.INVENTORY_RECEIPT, action = {AppAction.DELETE})
    @PostMapping("/{id}/archive")
    public GoodsReceiptResponseDTO archive(@PathVariable("id") Long id) {
        return service.archive(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_RECEIPT, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping
    public List<GoodsReceiptResponseDTO> listAll() {
        return service.listForCurrentCompany();
    }

    @RequiresPermission(module = AppModule.INVENTORY_RECEIPT, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/purchase-order/{poId}")
    public List<GoodsReceiptResponseDTO> list(@PathVariable("poId") Long poId) {
        return service.listByPO(poId);
    }

    @RequiresPermission(module = AppModule.INVENTORY_RECEIPT, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/{id}")
    public GoodsReceiptResponseDTO get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_RECEIPT, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/{id}/pdf")
    public String getReceiptPdfUrl(@PathVariable("id") Long id) {
        return service.getOrCreateReceiptPdfUrl(id);
    }
}