package com.erp.controller.finance;

import com.erp.dto.finance.InvoiceResponse;
import com.erp.service.finance.InvoiceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/invoices")
public class PublicInvoiceController {

    private final InvoiceService invoiceService;

    public PublicInvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping("/{invoiceCode}")
    public ResponseEntity<InvoiceResponse> getPublicInvoice(
            @PathVariable("invoiceCode") String invoiceCode) {
        return ResponseEntity.ok(invoiceService.getInvoiceByCode(invoiceCode));
    }
}
