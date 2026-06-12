package com.erp.service.finance;

import com.erp.domain.InvoiceType;
import com.erp.domain.finance.Payment;
import com.erp.domain.finance.PaymentDirection;
import com.erp.domain.purchase.PurchaseOrder;
import com.erp.domain.purchase.PurchaseOrderStatus;
import com.erp.repo.finance.InvoiceRepository;
import com.erp.repo.finance.PaymentRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.DocumentSequenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Vendor payables for purchase orders — kept separate from {@link PaymentService} to avoid a
 * Spring bean cycle (PaymentService → InvoiceService → … → PurchaseOrderService).
 */
@Service
public class VendorPayableService {

    private final PaymentRepository paymentRepo;
    private final InvoiceRepository invoiceRepo;
    private final AuthContext auth;
    private final DocumentSequenceService documentSequenceService;

    public VendorPayableService(
            PaymentRepository paymentRepo,
            InvoiceRepository invoiceRepo,
            AuthContext auth,
            DocumentSequenceService documentSequenceService) {
        this.paymentRepo = paymentRepo;
        this.invoiceRepo = invoiceRepo;
        this.auth = auth;
        this.documentSequenceService = documentSequenceService;
    }

    /**
     * Creates a pending vendor payable when the PO is released to the supplier (CONFIRMED+).
     * Idempotent if a pending vendor payable already exists for the PO.
     */
    @Transactional
    public void createVendorPayableForPurchaseOrder(PurchaseOrder po) {
        if (po == null || po.getId() == null) {
            return;
        }
        if (!isReleasedToSupplier(po.getStatus())) {
            return;
        }
        if (paymentRepo.existsByPurchaseOrderIdAndPaymentDirectionAndPaymentMethod(
                po.getId(), PaymentDirection.VENDOR, "PENDING_VENDOR_PAYMENT")) {
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
     * Creates a follow-up pending vendor payable for the remaining outstanding balance after a partial payment.
     */
    @Transactional
    public void ensurePendingVendorPayableForOutstanding(PurchaseOrder po, BigDecimal outstanding) {
        if (po == null || po.getId() == null) {
            return;
        }
        if (!isReleasedToSupplier(po.getStatus())) {
            return;
        }
        if (outstanding == null || outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (paymentRepo.existsByPurchaseOrderIdAndPaymentDirectionAndPaymentMethod(
                po.getId(), PaymentDirection.VENDOR, "PENDING_VENDOR_PAYMENT")) {
            return;
        }
        Long userId = auth.getCurrentUserId();
        String purchaseInvoiceCode = invoiceRepo.findByOrderIdAndType(po.getId(), InvoiceType.PURCHASE)
                .map(inv -> inv.getInvoiceId())
                .orElse(null);
        Payment payment = Payment.builder()
                .paymentCode(documentSequenceService.generateNext("VAP"))
                .company(po.getCompany())
                .amount(outstanding)
                .paymentMethod("PENDING_VENDOR_PAYMENT")
                .effectiveDate(LocalDate.now())
                .notes("Vendor payable balance for purchase order " + po.getOrderNumber())
                .invoiceId(purchaseInvoiceCode)
                .paymentDirection(PaymentDirection.VENDOR)
                .purchaseOrderId(po.getId())
                .createdBy(userId)
                .build();
        payment.setPdfUrl("https://dummy.url/payments/" + payment.getPaymentCode() + ".pdf");
        paymentRepo.save(payment);
    }

    /**
     * Whether the purchase invoice linked to this PO is fully paid.
     */
    public boolean isVendorPaymentSettledForPurchaseOrder(Long purchaseOrderId) {
        return invoiceRepo.findByOrderIdAndType(purchaseOrderId, InvoiceType.PURCHASE)
                .map(inv -> {
                    if ("PAID".equalsIgnoreCase(inv.getStatus())) {
                        return true;
                    }
                    BigDecimal outstanding = inv.getOutstanding();
                    return outstanding != null && outstanding.compareTo(BigDecimal.ZERO) <= 0;
                })
                .orElse(false);
    }

    public boolean hasPendingVendorPayable(Long purchaseOrderId) {
        return paymentRepo.existsByPurchaseOrderIdAndPaymentDirectionAndPaymentMethod(
                purchaseOrderId, PaymentDirection.VENDOR, "PENDING_VENDOR_PAYMENT");
    }

    public Optional<Long> findVendorPaymentIdForPurchaseOrder(Long purchaseOrderId) {
        List<Payment> payments = paymentRepo.findByPurchaseOrderIdAndPaymentDirectionOrderByCreatedAtDesc(
                purchaseOrderId, PaymentDirection.VENDOR);
        Optional<Long> pending = payments.stream()
                .filter(p -> isPendingVendorPayment(p.getPaymentMethod()))
                .findFirst()
                .map(Payment::getId);
        if (pending.isPresent()) {
            return pending;
        }
        return payments.stream().findFirst().map(Payment::getId);
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
