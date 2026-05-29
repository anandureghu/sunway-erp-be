package com.erp.service.finance;

import com.erp.domain.finance.Payment;
import com.erp.domain.finance.PaymentDirection;
import com.erp.domain.purchase.PurchaseOrder;
import com.erp.domain.purchase.PurchaseOrderStatus;
import com.erp.repo.finance.PaymentRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.DocumentSequenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Vendor payables for purchase orders — kept separate from {@link PaymentService} to avoid a
 * Spring bean cycle (PaymentService → InvoiceService → … → PurchaseOrderService).
 */
@Service
public class VendorPayableService {

    private final PaymentRepository paymentRepo;
    private final AuthContext auth;
    private final DocumentSequenceService documentSequenceService;

    public VendorPayableService(PaymentRepository paymentRepo, AuthContext auth, DocumentSequenceService documentSequenceService) {
        this.paymentRepo = paymentRepo;
        this.auth = auth;
        this.documentSequenceService = documentSequenceService;
    }

    /**
     * Creates a pending vendor payable when the PO is released to the supplier (CONFIRMED+).
     * Idempotent if a vendor payable already exists for the PO.
     */
    @Transactional
    public void createVendorPayableForPurchaseOrder(PurchaseOrder po) {
        if (po == null || po.getId() == null) {
            return;
        }
        if (!isReleasedToSupplier(po.getStatus())) {
            return;
        }
        if (paymentRepo.findFirstByPurchaseOrderIdAndPaymentDirection(po.getId(), PaymentDirection.VENDOR)
                .isPresent()) {
            return;
        }
        if (po.getTotalAmount() == null || po.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Long userId = auth.getCurrentUserId();
        Payment payment = Payment.builder()
                .paymentCode(documentSequenceService.generateNext("VAP"))
                .company(po.getCompany())
                .amount(po.getTotalAmount())
                .paymentMethod("PENDING_VENDOR_PAYMENT")
                .effectiveDate(po.getOrderDate() != null ? po.getOrderDate() : LocalDate.now())
                .notes("Vendor payable for purchase order " + po.getOrderNumber())
                .invoiceId(null)
                .paymentDirection(PaymentDirection.VENDOR)
                .purchaseOrderId(po.getId())
                .createdBy(userId)
                .build();
        payment.setPdfUrl("https://dummy.url/payments/" + payment.getPaymentCode() + ".pdf");
        paymentRepo.save(payment);
    }

    /**
     * Whether finance has confirmed the vendor payment in AP (not merely released to supplier).
     */
    public boolean isVendorPaymentSettledForPurchaseOrder(Long purchaseOrderId) {
        return paymentRepo.findFirstByPurchaseOrderIdAndPaymentDirection(
                        purchaseOrderId, PaymentDirection.VENDOR)
                .map(p -> !isPendingVendorPayment(p.getPaymentMethod()))
                .orElse(false);
    }

    public boolean hasPendingVendorPayable(Long purchaseOrderId) {
        return paymentRepo.findFirstByPurchaseOrderIdAndPaymentDirection(
                        purchaseOrderId, PaymentDirection.VENDOR)
                .map(p -> isPendingVendorPayment(p.getPaymentMethod()))
                .orElse(false);
    }

    private static boolean isReleasedToSupplier(PurchaseOrderStatus status) {
        return status == PurchaseOrderStatus.CONFIRMED
                || status == PurchaseOrderStatus.PARTIALLY_RECEIVED
                || status == PurchaseOrderStatus.RECEIVED;
    }

    private static boolean isPendingVendorPayment(String method) {
        return method != null && "PENDING_VENDOR_PAYMENT".equalsIgnoreCase(method.trim());
    }
}
