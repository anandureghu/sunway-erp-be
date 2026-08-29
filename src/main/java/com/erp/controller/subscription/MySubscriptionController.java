package com.erp.controller.subscription;

import com.erp.dto.subscription.CompanySubscriptionResponse;
import com.erp.service.subscription.SubscriptionInvoiceService;
import com.erp.service.subscription.SubscriptionPaymentReceiptService;
import com.erp.service.subscription.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Company-scoped subscription billing for ADMIN (own company only).
 */
@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class MySubscriptionController {

    private final SubscriptionService subscriptionService;
    private final SubscriptionInvoiceService invoiceService;
    private final SubscriptionPaymentReceiptService receiptService;

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public CompanySubscriptionResponse mySubscription() {
        return subscriptionService.getMySubscription();
    }

    @GetMapping("/me/invoices/{invoiceId}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> downloadMyInvoicePdf(@PathVariable Long invoiceId) {
        Long companyId = subscriptionService.getMySubscription().getCompanyId();
        byte[] pdf = invoiceService.downloadPdf(companyId, invoiceId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"subscription-invoice-" + invoiceId + ".pdf\"")
                .body(pdf);
    }

    @GetMapping("/me/payments/{paymentId}/receipt/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> downloadMyPaymentReceiptPdf(@PathVariable Long paymentId) {
        Long companyId = subscriptionService.getMySubscription().getCompanyId();
        byte[] pdf = receiptService.downloadReceiptPdf(companyId, paymentId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"subscription-receipt-" + paymentId + ".pdf\"")
                .body(pdf);
    }
}
