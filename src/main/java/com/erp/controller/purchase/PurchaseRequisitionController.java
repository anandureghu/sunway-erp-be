package com.erp.controller.purchase;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.purchase.PurchaseRequisitionCreateDTO;
import com.erp.dto.purchase.PurchaseRequisitionDocumentDTO;
import com.erp.dto.purchase.PurchaseRequisitionResponseDTO;
import com.erp.dto.purchase.PurchaseRequisitionReviewDTO;
import com.erp.service.purchase.PurchaseRequisitionService;
import com.erp.service.security.annotation.RequiresPermission;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/purchase/requisitions")
public class PurchaseRequisitionController {

    private final PurchaseRequisitionService service;

    public PurchaseRequisitionController(PurchaseRequisitionService service) {
        this.service = service;
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.CREATE})
    @PostMapping
    public PurchaseRequisitionResponseDTO create(
            @RequestBody PurchaseRequisitionCreateDTO dto
    ) {
        return service.create(dto);
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.EDIT})
    @PostMapping("/{id}/submit")
    public PurchaseRequisitionResponseDTO submit(@PathVariable("id") Long id) {
        return service.submit(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.APPROVE})
    @PostMapping("/{id}/approve")
    public PurchaseRequisitionResponseDTO approve(@PathVariable("id") Long id) {
        return service.approve(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.APPROVE})
    @PostMapping("/{id}/reject")
    public PurchaseRequisitionResponseDTO reject(
            @PathVariable("id") Long id,
            @RequestBody PurchaseRequisitionReviewDTO dto
    ) {
        return service.reject(id, dto);
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.APPROVE, AppAction.EDIT})
    @PostMapping("/{id}/send-back")
    public PurchaseRequisitionResponseDTO sendBack(
            @PathVariable("id") Long id,
            @RequestBody PurchaseRequisitionReviewDTO dto
    ) {
        return service.sendBack(id, dto);
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.EDIT})
    @PostMapping("/{id}/revise")
    public PurchaseRequisitionResponseDTO revise(@PathVariable("id") Long id) {
        return service.revise(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.EDIT})
    @PutMapping("/{id}")
    public PurchaseRequisitionResponseDTO update(
            @PathVariable("id") Long id,
            @RequestBody PurchaseRequisitionCreateDTO dto
    ) {
        return service.update(id, dto);
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.DELETE})
    @PostMapping("/{id}/archive")
    public PurchaseRequisitionResponseDTO archive(@PathVariable("id") Long id) {
        return service.archive(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping
    public List<PurchaseRequisitionResponseDTO> list() {
        return service.list();
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/{id}")
    public PurchaseRequisitionResponseDTO get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.EDIT, AppAction.CREATE})
    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PurchaseRequisitionDocumentDTO uploadDocument(
            @PathVariable("id") Long id,
            @RequestPart("file") MultipartFile file
    ) {
        return service.uploadDocument(id, file);
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/{id}/documents")
    public List<PurchaseRequisitionDocumentDTO> listDocuments(@PathVariable("id") Long id) {
        return service.listDocuments(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.DELETE, AppAction.EDIT})
    @DeleteMapping("/{id}/documents/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable("id") Long id,
            @PathVariable("documentId") Long documentId
    ) {
        service.deleteDocument(id, documentId);
        return ResponseEntity.noContent().build();
    }
}
