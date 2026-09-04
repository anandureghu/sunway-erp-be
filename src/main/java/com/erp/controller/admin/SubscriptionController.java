package com.erp.controller.admin;

import com.erp.domain.subscription.SubscriptionPaymentStatus;
import com.erp.domain.subscription.SubscriptionPlanType;
import com.erp.domain.subscription.SubscriptionStatus;
import com.erp.dto.common.PagedResponse;
import com.erp.dto.subscription.*;
import com.erp.service.subscription.SubscriptionInvoiceService;
import com.erp.service.subscription.SubscriptionPaymentReceiptService;
import com.erp.service.subscription.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final SubscriptionInvoiceService invoiceService;
    private final SubscriptionPaymentReceiptService receiptService;

    public SubscriptionController(
            SubscriptionService subscriptionService,
            SubscriptionInvoiceService invoiceService,
            SubscriptionPaymentReceiptService receiptService
    ) {
        this.subscriptionService = subscriptionService;
        this.invoiceService = invoiceService;
        this.receiptService = receiptService;
    }

    @GetMapping("/me/status")
    public SubscriptionStatusResponse myStatus() {
        return subscriptionService.getMyStatus();
    }

    @GetMapping("/analytics")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public SubscriptionAnalyticsResponse analytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return subscriptionService.analytics(from, to);
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PagedResponse<CompanySubscriptionResponse> list(
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(required = false) SubscriptionPlanType planType,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Integer expiringWithinDays,
            @RequestParam(required = false) SubscriptionPaymentStatus paymentStatus,
            @PageableDefault(size = 20, sort = "endsAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return PagedResponse.from(
                subscriptionService.list(
                        status, planType, companyId, expiringWithinDays, paymentStatus, pageable)
        );
    }

    @GetMapping("/{companyId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public CompanySubscriptionResponse get(@PathVariable Long companyId) {
        return subscriptionService.getByCompanyId(companyId);
    }

    @PutMapping("/{companyId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public CompanySubscriptionResponse assign(
            @PathVariable Long companyId,
            @Valid @RequestBody AssignSubscriptionRequest request
    ) {
        return subscriptionService.assign(companyId, request);
    }

    @PostMapping("/{companyId}/payments")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public CompanySubscriptionResponse recordPayment(
            @PathVariable Long companyId,
            @Valid @RequestBody RecordSubscriptionPaymentRequest request
    ) {
        return subscriptionService.recordPayment(companyId, request);
    }

    @PostMapping("/{companyId}/extend")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public CompanySubscriptionResponse extend(
            @PathVariable Long companyId,
            @Valid @RequestBody ExtendSubscriptionRequest request
    ) {
        return subscriptionService.extend(companyId, request);
    }

    @PostMapping("/{companyId}/cancel")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public CompanySubscriptionResponse cancel(
            @PathVariable Long companyId,
            @RequestBody(required = false) CancelSubscriptionRequest request
    ) {
        return subscriptionService.cancel(
                companyId, request != null ? request : new CancelSubscriptionRequest());
    }

    @PostMapping("/{companyId}/invoices/generate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public SubscriptionInvoiceResponse generateInvoice(@PathVariable Long companyId) {
        return invoiceService.generateForCompany(companyId);
    }

    @PostMapping("/{companyId}/invoices/regenerate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public SubscriptionInvoiceResponse regenerateInvoice(@PathVariable Long companyId) {
        return invoiceService.regenerateForCompany(companyId);
    }

    @PostMapping("/{companyId}/invoices/send")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public SubscriptionInvoiceResponse sendInvoice(
            @PathVariable Long companyId,
            @RequestParam(defaultValue = "false") boolean resend
    ) {
        return invoiceService.sendForCompany(companyId, resend);
    }

    @GetMapping("/{companyId}/invoices/{invoiceId}/pdf")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<byte[]> downloadInvoicePdf(
            @PathVariable Long companyId,
            @PathVariable Long invoiceId
    ) {
        byte[] pdf = invoiceService.downloadPdf(companyId, invoiceId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"subscription-invoice-" + invoiceId + ".pdf\"")
                .body(pdf);
    }

    @GetMapping("/{companyId}/payments/{paymentId}/receipt/pdf")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<byte[]> downloadPaymentReceiptPdf(
            @PathVariable Long companyId,
            @PathVariable Long paymentId
    ) {
        byte[] pdf = receiptService.downloadReceiptPdf(companyId, paymentId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"subscription-receipt-" + paymentId + ".pdf\"")
                .body(pdf);
    }

    @PostMapping("/{companyId}/payments/{paymentId}/receipt/send")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public SubscriptionPaymentResponse sendPaymentReceipt(
            @PathVariable Long companyId,
            @PathVariable Long paymentId,
            @RequestParam(defaultValue = "false") boolean resend
    ) {
        return receiptService.sendReceipt(companyId, paymentId, resend);
    }
}
