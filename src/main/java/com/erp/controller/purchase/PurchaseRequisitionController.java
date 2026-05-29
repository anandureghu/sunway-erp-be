package com.erp.controller.purchase;

import com.erp.dto.purchase.PurchaseRequisitionCreateDTO;
import com.erp.dto.purchase.PurchaseRequisitionDocumentDTO;
import com.erp.dto.purchase.PurchaseRequisitionResponseDTO;
import com.erp.dto.purchase.PurchaseRequisitionReviewDTO;
import com.erp.service.purchase.PurchaseRequisitionService;
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

    @PostMapping
    public PurchaseRequisitionResponseDTO create(
            @RequestBody PurchaseRequisitionCreateDTO dto
    ) {
        return service.create(dto);
    }

    @PostMapping("/{id}/submit")
    public PurchaseRequisitionResponseDTO submit(@PathVariable("id") Long id) {
        return service.submit(id);
    }

    @PostMapping("/{id}/approve")
    public PurchaseRequisitionResponseDTO approve(@PathVariable("id") Long id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/reject")
    public PurchaseRequisitionResponseDTO reject(
            @PathVariable("id") Long id,
            @RequestBody PurchaseRequisitionReviewDTO dto
    ) {
        return service.reject(id, dto);
    }

    @PostMapping("/{id}/send-back")
    public PurchaseRequisitionResponseDTO sendBack(
            @PathVariable("id") Long id,
            @RequestBody PurchaseRequisitionReviewDTO dto
    ) {
        return service.sendBack(id, dto);
    }

    @PostMapping("/{id}/revise")
    public PurchaseRequisitionResponseDTO revise(@PathVariable("id") Long id) {
        return service.revise(id);
    }

    @PutMapping("/{id}")
    public PurchaseRequisitionResponseDTO update(
            @PathVariable("id") Long id,
            @RequestBody PurchaseRequisitionCreateDTO dto
    ) {
        return service.update(id, dto);
    }

    @PostMapping("/{id}/archive")
    public PurchaseRequisitionResponseDTO archive(@PathVariable("id") Long id) {
        return service.archive(id);
    }

    @GetMapping
    public List<PurchaseRequisitionResponseDTO> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public PurchaseRequisitionResponseDTO get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PurchaseRequisitionDocumentDTO uploadDocument(
            @PathVariable("id") Long id,
            @RequestPart("file") MultipartFile file
    ) {
        return service.uploadDocument(id, file);
    }

    @GetMapping("/{id}/documents")
    public List<PurchaseRequisitionDocumentDTO> listDocuments(@PathVariable("id") Long id) {
        return service.listDocuments(id);
    }

    @DeleteMapping("/{id}/documents/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable("id") Long id,
            @PathVariable("documentId") Long documentId
    ) {
        service.deleteDocument(id, documentId);
        return ResponseEntity.noContent().build();
    }
}
