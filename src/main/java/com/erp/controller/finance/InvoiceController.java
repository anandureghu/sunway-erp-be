package com.erp.controller.finance;

import com.erp.domain.InvoiceType;
import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.finance.InvoicePdfTextPreviewResponse;
import com.erp.dto.finance.InvoiceRequest;
import com.erp.dto.finance.InvoiceResponse;
import com.erp.service.finance.InvoicePdfTextPreviewService;
import com.erp.service.finance.InvoiceService;
import com.erp.service.security.annotation.RequiresPermission;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoicePdfTextPreviewService pdfTextPreviewService;

    public InvoiceController(
            InvoiceService invoiceService,
            InvoicePdfTextPreviewService pdfTextPreviewService
    ) {
        this.invoiceService = invoiceService;
        this.pdfTextPreviewService = pdfTextPreviewService;
    }

    @RequiresPermission(module = AppModule.FINANCE_INVOICE, action = {AppAction.CREATE})
    @PostMapping
    public ResponseEntity<InvoiceResponse> createInvoice(@RequestBody InvoiceRequest request) {
        return ResponseEntity.ok(invoiceService.createInvoice(request));
    }

    @RequiresPermission(module = AppModule.FINANCE_INVOICE, action = {AppAction.CREATE})
    @PostMapping(value = "/with-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InvoiceResponse> createInvoiceWithDocument(
            @RequestPart("invoice") InvoiceRequest invoice,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        return ResponseEntity.ok(invoiceService.createInvoice(invoice, file));
    }

    @RequiresPermission(module = AppModule.FINANCE_INVOICE, action = {AppAction.EDIT})
    @PostMapping(value = "/{id}/supplier-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InvoiceResponse> attachSupplierDocument(
            @PathVariable("id") Long id,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.ok(invoiceService.attachSupplierDocument(id, file));
    }

    @RequiresPermission(module = AppModule.FINANCE_INVOICE, action = {AppAction.EDIT})
    @PostMapping(value = "/{id}/match-vendor-invoice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InvoiceResponse> matchVendorInvoice(
            @PathVariable("id") Long id,
            @RequestPart("vendorInvoiceNumber") String vendorInvoiceNumber,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        return ResponseEntity.ok(invoiceService.matchVendorInvoice(id, vendorInvoiceNumber, file));
    }

    @RequiresPermission(module = AppModule.FINANCE_INVOICE, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @PostMapping(value = "/preview-pdf-text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InvoicePdfTextPreviewResponse> previewPdfText(
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.ok(pdfTextPreviewService.extractPreview(file));
    }

    @RequiresPermission(module = AppModule.FINANCE_INVOICE, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping
    public ResponseEntity<List<InvoiceResponse>> listInvoices(
            @RequestParam(required = false) InvoiceType type
    ) {
        return ResponseEntity.ok(invoiceService.listInvoicesForCurrentCompany(type));
    }

    @RequiresPermission(module = AppModule.FINANCE_INVOICE, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getInvoiceById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(invoiceService.getInvoiceById(id));
    }

    @RequiresPermission(module = AppModule.FINANCE_INVOICE, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/code/{invoiceCode}")
    public ResponseEntity<InvoiceResponse> getInvoiceByCode(@PathVariable("invoiceCode") String invoiceCode) {
        return ResponseEntity.ok(invoiceService.getInvoiceByCode(invoiceCode));
    }

    @RequiresPermission(module = AppModule.FINANCE_INVOICE, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/{id}/pdf")
    public ResponseEntity<String> getInvoicePdfUrl(@PathVariable("id") Long id) {
        String pdfUrl = invoiceService.getOrCreateInvoicePdfUrl(id);
        return ResponseEntity.ok(pdfUrl);
    }

    @RequiresPermission(module = AppModule.FINANCE_INVOICE, action = {AppAction.EDIT})
    @PostMapping("/{id}/email")
    public ResponseEntity<Void> sendInvoiceEmail(@PathVariable("id") Long id) {
        invoiceService.emailInvoice(id);
        return ResponseEntity.ok().build();
    }

    @RequiresPermission(module = AppModule.FINANCE_INVOICE, action = {AppAction.EDIT})
    @PostMapping("/{id}/receipt-email")
    public ResponseEntity<Void> sendReceiptEmail(@PathVariable("id") Long id) {
        invoiceService.emailReceipt(id);
        return ResponseEntity.ok().build();
    }


    @RequiresPermission(module = AppModule.FINANCE_INVOICE, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<InvoiceResponse>> getInvoicesByCustomer(@PathVariable("customerId") String customerId) {
        return ResponseEntity.ok(invoiceService.getInvoicesByCustomer(customerId));
    }

    @RequiresPermission(module = AppModule.FINANCE_INVOICE, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/status/{status}/{companyId}")
    public ResponseEntity<List<InvoiceResponse>> getInvoicesByStatus(@PathVariable("status") String status, @PathVariable("companyId") Long companyId) {
        return ResponseEntity.ok(invoiceService.getInvoicesByStatus(companyId, status));
    }

    @RequiresPermission(module = AppModule.FINANCE_INVOICE, action = {AppAction.EDIT})
    @PutMapping("/{id}")
    public ResponseEntity<InvoiceResponse> updateInvoice(@PathVariable("id") Long id, @RequestBody InvoiceRequest request) {
        return ResponseEntity.ok(invoiceService.updateInvoice(id, request));
    }

    @RequiresPermission(module = AppModule.FINANCE_INVOICE, action = {AppAction.DELETE})
    @PostMapping("/{id}/archive")
    public ResponseEntity<InvoiceResponse> archiveInvoice(@PathVariable("id") Long id) {
        return ResponseEntity.ok(invoiceService.archiveInvoice(id));
    }

    @RequiresPermission(module = AppModule.FINANCE_INVOICE, action = {AppAction.DELETE})
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInvoice(@PathVariable("id") Long id) {
        invoiceService.deleteInvoice(id);
        return ResponseEntity.noContent().build();
    }
}
