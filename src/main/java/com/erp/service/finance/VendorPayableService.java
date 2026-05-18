package com.erp.service.finance;

import com.erp.domain.finance.Payment;
import com.erp.domain.finance.PaymentDirection;
import com.erp.domain.purchase.PurchaseOrder;
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
     * Creates a pending vendor payable row when a purchase order is created (AP).
     * Idempotent if a vendor payable already exists for the PO.
     */
    @Transactional
    public void createVendorPayableForPurchaseOrder(PurchaseOrder po) {
        if (po == null || po.getId() == null) {
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
     * Whether the PO may be released to the supplier: vendor payable must be confirmed,
     * unless there is no vendor payable row (legacy orders).
     */
    public boolean isVendorPaymentSettledForPurchaseOrder(Long purchaseOrderId) {
        Optional<Payment> opt = paymentRepo.findFirstByPurchaseOrderIdAndPaymentDirection(
                purchaseOrderId, PaymentDirection.VENDOR);
        if (opt.isEmpty()) {
            return true;
        }
        String method = opt.get().getPaymentMethod();
        return method != null && !"PENDING_VENDOR_PAYMENT".equalsIgnoreCase(method);
    }
}
