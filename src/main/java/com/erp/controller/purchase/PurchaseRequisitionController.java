package com.erp.controller.purchase;

import com.erp.dto.purchase.PurchaseRequisitionCreateDTO;
import com.erp.dto.purchase.PurchaseRequisitionResponseDTO;
import com.erp.service.purchase.PurchaseRequisitionService;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping
    public List<PurchaseRequisitionResponseDTO> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public PurchaseRequisitionResponseDTO get(@PathVariable("id") Long id) {
        return service.get(id);
    }
}
