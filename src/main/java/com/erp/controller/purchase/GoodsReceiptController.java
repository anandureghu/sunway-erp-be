package com.erp.controller.purchase;

import com.erp.dto.purchase.GoodsReceiptCreateDTO;
import com.erp.dto.purchase.GoodsReceiptResponseDTO;
import com.erp.service.purchase.GoodsReceiptService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/purchase/receipts")
public class GoodsReceiptController {

    private final GoodsReceiptService service;

    public GoodsReceiptController(GoodsReceiptService service) {
        this.service = service;
    }

    @PostMapping
    public GoodsReceiptResponseDTO receive(@RequestBody GoodsReceiptCreateDTO dto) {
        return service.receive(dto);
    }

    @GetMapping
    public List<GoodsReceiptResponseDTO> listAll() {
        return service.listForCurrentCompany();
    }

    @GetMapping("/purchase-order/{poId}")
    public List<GoodsReceiptResponseDTO> list(@PathVariable("poId") Long poId) {
        return service.listByPO(poId);
    }

    @GetMapping("/{id}")
    public GoodsReceiptResponseDTO get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @GetMapping("/{id}/pdf")
    public String getReceiptPdfUrl(@PathVariable("id") Long id) {
        return service.getOrCreateReceiptPdfUrl(id);
    }
}
