package com.erp.controller.finance;

import com.erp.dto.finance.InvoiceRequest;
import com.erp.dto.finance.InvoiceResponse;
import com.erp.service.finance.InvoiceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping
    public ResponseEntity<InvoiceResponse> createInvoice(@RequestBody InvoiceRequest request) {
        return ResponseEntity.ok(invoiceService.createInvoice(request));
    }

    @GetMapping
    public ResponseEntity<List<InvoiceResponse>> getAllInvoices() {
        return ResponseEntity.ok(invoiceService.getAllInvoices());
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getInvoiceById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(invoiceService.getInvoiceById(id));
    }

    @GetMapping("/code/{invoiceCode}")
    public ResponseEntity<InvoiceResponse> getInvoiceByCode(@PathVariable("invoiceCode") String invoiceCode) {
        return ResponseEntity.ok(invoiceService.getInvoiceByCode(invoiceCode));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<String> getInvoicePdfUrl(@PathVariable("id") Long id) {
        String pdfUrl = invoiceService.getOrCreateInvoicePdfUrl(id);
        return ResponseEntity.ok(pdfUrl);
    }

    @PostMapping("/{id}/email")
    public ResponseEntity<Void> sendInvoiceEmail(@PathVariable("id") Long id) {
        invoiceService.emailInvoice(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/receipt-email")
    public ResponseEntity<Void> sendReceiptEmail(@PathVariable("id") Long id) {
        invoiceService.emailReceipt(id);
        return ResponseEntity.ok().build();
    }


    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<InvoiceResponse>> getInvoicesByCustomer(@PathVariable("customerId") String customerId) {
        return ResponseEntity.ok(invoiceService.getInvoicesByCustomer(customerId));
    }

    @GetMapping("/status/{status}/{companyId}")
    public ResponseEntity<List<InvoiceResponse>> getInvoicesByStatus(@PathVariable("status") String status, @PathVariable("companyId") Long companyId) {
        return ResponseEntity.ok(invoiceService.getInvoicesByStatus(companyId, status));
    }

    @PutMapping("/{id}")
    public ResponseEntity<InvoiceResponse> updateInvoice(@PathVariable("id") Long id, @RequestBody InvoiceRequest request) {
        return ResponseEntity.ok(invoiceService.updateInvoice(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInvoice(@PathVariable("id") Long id) {
        invoiceService.deleteInvoice(id);
        return ResponseEntity.noContent().build();
    }
}
