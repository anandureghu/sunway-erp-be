package com.erp.service.finance;

import com.erp.domain.finance.Payment;
import com.erp.domain.finance.PaymentDirection;
import com.erp.domain.purchase.PurchaseOrder;
import com.erp.domain.purchase.PurchaseOrderStatus;
import com.erp.domain.purchase.PurchaseRequisition;
import com.erp.exception.ConflictException;
import com.erp.domain.hr.Company;
import com.erp.dto.finance.ConfirmPaymentDTO;
import com.erp.dto.finance.CreatePaymentDTO;
import com.erp.dto.finance.CreateTransactionDTO;
import com.erp.dto.finance.PaymentResponseDTO;
import com.erp.util.PaymentMethodLabels;
import com.erp.dto.finance.TransactionResponseDTO;
import com.erp.repo.finance.InvoiceRepository;
import com.erp.repo.finance.PaymentRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.purchase.PurchaseOrderRepository;
import com.erp.repo.purchase.PurchaseRequisitionRepository;
import com.erp.repo.sales.SalesOrderRepository;
import com.erp.security.context.AuthContext;
import com.erp.domain.InvoiceType;
import com.erp.domain.finance.Invoice;
import com.erp.domain.inventory.Vendor;
import com.erp.service.notification.CustomerEmailService;
import com.erp.service.DocumentSequenceService;
import com.erp.service.pdf.VendorPaymentReceiptPdfService;
import com.erp.service.purchase.PurchasePostingAccountsResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepo;
    private final TransactionService transactionService;
    private final InvoiceService invoiceService;
    private final SalesOrderRepository salesOrderRepo;
    private final CustomerEmailService customerEmailService;
    private final CompanyRepository companyRepo;
    private final PurchaseOrderRepository purchaseOrderRepo;
    private final PurchaseRequisitionRepository purchaseRequisitionRepo;
    private final InvoiceRepository invoiceRepo;
    private final AuthContext auth;
    private final DocumentSequenceService documentSequenceService;
    private final VendorPaymentReceiptPdfService vendorPaymentReceiptPdfService;
    private final VendorPayableService vendorPayableService;
    private final CompanyAccountingDefaultsService accountingDefaults;

    public PaymentService(PaymentRepository paymentRepo,
                          TransactionService transactionService,
                          InvoiceService invoiceService,
                          SalesOrderRepository salesOrderRepo,
                          CustomerEmailService customerEmailService,
                          CompanyRepository companyRepo,
                          PurchaseOrderRepository purchaseOrderRepo,
                          PurchaseRequisitionRepository purchaseRequisitionRepo,
                          InvoiceRepository invoiceRepo,
                          AuthContext auth,
                          DocumentSequenceService documentSequenceService,
                          VendorPaymentReceiptPdfService vendorPaymentReceiptPdfService,
                          VendorPayableService vendorPayableService,
                          CompanyAccountingDefaultsService accountingDefaults) {

        this.paymentRepo = paymentRepo;
        this.transactionService = transactionService;
        this.invoiceService = invoiceService;
        this.salesOrderRepo = salesOrderRepo;
        this.customerEmailService = customerEmailService;
        this.companyRepo = companyRepo;
        this.purchaseOrderRepo = purchaseOrderRepo;
        this.purchaseRequisitionRepo = purchaseRequisitionRepo;
        this.invoiceRepo = invoiceRepo;
        this.auth = auth;
        this.documentSequenceService = documentSequenceService;
        this.vendorPaymentReceiptPdfService = vendorPaymentReceiptPdfService;
        this.vendorPayableService = vendorPayableService;
        this.accountingDefaults = accountingDefaults;
    }

    @Transactional
    public PaymentResponseDTO createPayment(CreatePaymentDTO dto) {
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Payment amount must be greater than zero");
        }
        if (dto.getInvoiceId() == null || dto.getInvoiceId().isBlank()) {
            throw new RuntimeException("Invoice ID is required");
        }
        if (dto.getPaymentMethod() == null || dto.getPaymentMethod().isBlank()) {
            throw new RuntimeException("Payment method is required");
        }

        Company company = companyRepo.findById(dto.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found"));

        Long userId = auth.getCurrentUserId();

//        if (!company.getCreatedBy().equals(String.valueOf(userId))) {
//            throw new RuntimeException("Not allowed");
//        }

//        // 2️⃣ Invoice → create or update
//        Invoice invoice;
//        if (dto.getInvoiceId() == null || dto.getInvoiceId().isBlank()) {
//            invoice = invoiceService.autoGenerateInvoice(company, dto.getAmount(), dto.getNotes());
//        } else {
//            invoice = invoiceService.applyPayment(dto.getInvoiceId(), dto.getAmount());
//        }

        // 3️⃣ Payment record
        Payment payment = Payment.builder()
                .paymentCode(documentSequenceService.generateNext("PAY"))
                .company(company)
                .amount(dto.getAmount())
                .paymentMethod(dto.getPaymentMethod())
                .effectiveDate(dto.getEffectiveDate() == null ? LocalDate.now() : dto.getEffectiveDate())
                .notes(dto.getNotes())
                .invoiceId(dto.getInvoiceId())
                .paymentDirection(PaymentDirection.CUSTOMER)
                .createdBy(userId)
                .build();

        payment.setPdfUrl("https://dummy.url/payments/" + payment.getPaymentCode() + ".pdf");

        Payment saved = paymentRepo.save(payment);
        Invoice invoice = invoiceService.applyPayment(dto.getInvoiceId(), dto.getAmount());
        postPaymentToAccounting(saved, invoice);
        if ("PAID".equalsIgnoreCase(invoice.getStatus())) {
            salesOrderRepo.findById(invoice.getOrderId())
                    .ifPresent(order -> customerEmailService.sendReceiptEmail(order.getCustomer(), invoice));
        }

//        // 4️⃣TODO: Create transaction
//        transactionService.createTransactionForPayment(
//                saved.getId(),
//                company.getId(),
//                dto.getAmount(),
//                coaService.getCompanyBankAccountCode(company.getId()),
//                coaService.getCustomerARAccountCode(company.getId()),
//                saved.getEffectiveDate(),
//                "PAYMENT"
//        );

        return toDTO(saved);
    }

    private PaymentResponseDTO toDTO(Payment p) {
        PaymentDirection dir = p.getPaymentDirection() != null
                ? p.getPaymentDirection()
                : PaymentDirection.CUSTOMER;
        PaymentResponseDTO.PaymentResponseDTOBuilder b = PaymentResponseDTO.builder()
                .id(p.getId())
                .paymentCode(p.getPaymentCode())
                .companyId(p.getCompany().getId())
                .amount(p.getAmount())
                .paymentMethod(p.getPaymentMethod())
                .effectiveDate(p.getEffectiveDate())
                .invoiceId(p.getInvoiceId())
                .paymentDirection(dir.name())
                .purchaseOrderId(p.getPurchaseOrderId())
                .pdfUrl(p.getPdfUrl())
                .archived(p.isArchived())
                .createdAt(p.getCreatedAt());
        if (p.getPurchaseOrderId() != null) {
            purchaseOrderRepo.findById(p.getPurchaseOrderId())
                    .ifPresent(po -> b.purchaseOrderNumber(po.getOrderNumber()));
        }
        enrichInvoiceAmounts(b, p);
        return b.build();
    }

    private void enrichInvoiceAmounts(PaymentResponseDTO.PaymentResponseDTOBuilder b, Payment p) {
        if (p.getInvoiceId() != null && !p.getInvoiceId().isBlank()) {
            invoiceRepo.findByInvoiceId(p.getInvoiceId()).ifPresent(inv -> {
                b.invoiceTotal(inv.getAmount());
                b.invoiceOutstanding(inv.getOutstanding() != null ? inv.getOutstanding() : inv.getAmount());
                if (inv.getType() == InvoiceType.SALES && inv.getOrderId() != null) {
                    salesOrderRepo.findById(inv.getOrderId())
                            .ifPresent(so -> b.salesOrderNumber(so.getOrderNumber()));
                }
            });
            return;
        }
        if (p.getPurchaseOrderId() != null) {
            invoiceRepo.findByOrderIdAndType(p.getPurchaseOrderId(), InvoiceType.PURCHASE).ifPresent(inv -> {
                b.invoiceTotal(inv.getAmount());
                b.invoiceOutstanding(inv.getOutstanding() != null ? inv.getOutstanding() : inv.getAmount());
            });
        }
    }

    private BigDecimal resolveConfirmAmount(ConfirmPaymentDTO body, Invoice invoice, Payment payment) {
        BigDecimal outstanding = invoice.getOutstanding() != null
                ? invoice.getOutstanding()
                : invoice.getAmount();
        if (outstanding == null || outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Invoice has no outstanding balance");
        }
        BigDecimal amount;
        if (body != null && body.getAmount() != null) {
            amount = body.getAmount();
        } else {
            BigDecimal pending = payment.getAmount() != null ? payment.getAmount() : outstanding;
            amount = pending.min(outstanding);
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Payment amount must be greater than zero");
        }
        if (amount.compareTo(outstanding) > 0) {
            throw new RuntimeException("Payment amount cannot exceed outstanding balance");
        }
        return amount;
    }

    private Invoice requireInvoiceForCustomerPayment(Payment payment) {
        if (payment.getInvoiceId() == null || payment.getInvoiceId().isBlank()) {
            throw new RuntimeException("Invoice ID is missing for this payment request");
        }
        return invoiceRepo.findByInvoiceId(payment.getInvoiceId())
                .orElseThrow(() -> new RuntimeException("Invoice not found for this payment"));
    }

    @Transactional
    public PaymentResponseDTO archivePayment(Long id) {
        Payment payment = paymentRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found"));
        assertPaymentInTenant(payment);
        if (payment.isArchived()) {
            return toDTO(payment);
        }
        String method = payment.getPaymentMethod() == null ? "" : payment.getPaymentMethod().trim();
        if ("PENDING_REQUEST".equalsIgnoreCase(method)
                || "PENDING_VENDOR_PAYMENT".equalsIgnoreCase(method)) {
            throw new RuntimeException("Only confirmed payments can be archived");
        }
        payment.setArchived(true);
        return toDTO(paymentRepo.save(payment));
    }

    public PaymentResponseDTO getPaymentById(Long id) {
        Payment p = paymentRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found"));
        assertPaymentInTenant(p);
        return toDTO(p);
    }

    public java.util.List<PaymentResponseDTO> getPaymentsForCompany(Long companyId) {
        assertTenantCompanyPath(companyId);
        return paymentRepo.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(this::toDTO)
                .toList();
    }

    public java.util.List<PaymentResponseDTO> getPaymentsForCompany(Long companyId, PaymentDirection direction) {
        assertTenantCompanyPath(companyId);
        if (direction == null) {
            return getPaymentsForCompany(companyId);
        }
        if (direction == PaymentDirection.CUSTOMER) {
            return paymentRepo.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                    .filter(p -> p.getPaymentDirection() == null
                            || p.getPaymentDirection() == PaymentDirection.CUSTOMER)
                    .map(this::toDTO)
                    .toList();
        }
        return paymentRepo.findByCompany_IdAndPaymentDirectionOrderByCreatedAtDesc(companyId, PaymentDirection.VENDOR).stream()
                .filter(this::isVendorPaymentVisibleInAccountsPayable)
                .map(this::toDTO)
                .toList();
    }

    private boolean isVendorPaymentVisibleInAccountsPayable(Payment payment) {
        if (payment.getPurchaseOrderId() == null) {
            return false;
        }
        return purchaseOrderRepo.findById(payment.getPurchaseOrderId())
                .map(po -> isPurchaseOrderEligibleForAccountsPayable(po.getStatus()))
                .orElse(false);
    }

    private static boolean isPurchaseOrderEligibleForAccountsPayable(PurchaseOrderStatus status) {
        return status == PurchaseOrderStatus.CONFIRMED
                || status == PurchaseOrderStatus.PARTIALLY_RECEIVED
                || status == PurchaseOrderStatus.RECEIVED;
    }

    public java.util.List<PaymentResponseDTO> getPaymentsByInvoice(String invoiceId) {
        if (invoiceId == null || invoiceId.isBlank()) {
            return List.of();
        }
        Invoice inv = invoiceRepo.findByInvoiceId(invoiceId).orElse(null);
        if (inv == null) {
            return List.of();
        }
        assertInvoiceCompany(inv);
        return paymentRepo.findByInvoiceIdOrderByCreatedAtDesc(invoiceId).stream()
                .filter(p -> isSuperAdmin()
                        || (auth.getCurrentCompanyId() != null
                        && p.getCompany() != null
                        && auth.getCurrentCompanyId().equals(p.getCompany().getId())))
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public PaymentResponseDTO updatePayment(Long id, CreatePaymentDTO dto) {
        Payment payment = paymentRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found"));
        assertPaymentInTenant(payment);
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Payment amount must be greater than zero");
        }
        if (dto.getPaymentMethod() == null || dto.getPaymentMethod().isBlank()) {
            throw new RuntimeException("Payment method is required");
        }

        payment.setAmount(dto.getAmount());
        payment.setPaymentMethod(dto.getPaymentMethod());
        payment.setEffectiveDate(dto.getEffectiveDate() == null ? LocalDate.now() : dto.getEffectiveDate());
        payment.setNotes(dto.getNotes());
        payment.setInvoiceId(dto.getInvoiceId());

        return toDTO(paymentRepo.save(payment));
    }

    @Transactional
    public PaymentResponseDTO confirmPayment(Long id, ConfirmPaymentDTO body) {
        Payment payment = paymentRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found"));
        assertPaymentInTenant(payment);

        PaymentDirection dir = payment.getPaymentDirection() != null
                ? payment.getPaymentDirection()
                : PaymentDirection.CUSTOMER;
        if (dir == PaymentDirection.VENDOR) {
            return confirmVendorPayment(payment, body);
        }

        if (!"PENDING_REQUEST".equalsIgnoreCase(payment.getPaymentMethod())) {
            throw new RuntimeException("Payment is already confirmed");
        }
        Invoice invoice = requireInvoiceForCustomerPayment(payment);
        BigDecimal confirmAmount = resolveConfirmAmount(body, invoice, payment);

        payment.setAmount(confirmAmount);
        payment.setPaymentMethod("BANK_TRANSFER");
        payment.setEffectiveDate(LocalDate.now());
        payment.setNotes(
                (payment.getNotes() == null ? "" : payment.getNotes() + " | ")
                        + "Confirmed from payment request"
        );
        Payment saved = paymentRepo.save(payment);

        Invoice updatedInvoice = invoiceService.applyPayment(saved.getInvoiceId(), saved.getAmount());
        postPaymentToAccounting(saved, updatedInvoice);
        if ("PARTIALLY_PAID".equalsIgnoreCase(updatedInvoice.getStatus())) {
            invoiceService.ensurePendingPaymentRequestForOutstanding(updatedInvoice);
        }
        if ("PAID".equalsIgnoreCase(updatedInvoice.getStatus())) {
            salesOrderRepo.findById(updatedInvoice.getOrderId())
                    .ifPresent(order -> customerEmailService.sendReceiptEmail(order.getCustomer(), updatedInvoice));
        }
        return toDTO(saved);
    }

    private PaymentResponseDTO confirmVendorPayment(Payment payment, ConfirmPaymentDTO body) {
        if (!PaymentMethodLabels.PENDING_VENDOR.equalsIgnoreCase(payment.getPaymentMethod())) {
            throw new RuntimeException("Vendor payment is already confirmed or is not pending");
        }
        if (payment.getPurchaseOrderId() == null) {
            throw new RuntimeException("Vendor payment is not linked to a purchase order");
        }
        PurchaseOrder po = purchaseOrderRepo.findById(payment.getPurchaseOrderId())
                .orElseThrow(() -> new RuntimeException("Purchase order not found for vendor payment"));
        if (!isPurchaseOrderEligibleForAccountsPayable(po.getStatus())) {
            throw new ConflictException(
                    "Release the purchase order to the supplier before confirming vendor payment in Accounts Payable.");
        }
        Invoice purchaseInvoice = invoiceRepo.findByOrderIdAndType(po.getId(), InvoiceType.PURCHASE)
                .orElseThrow(() -> new RuntimeException("Purchase invoice not found for this purchase order"));
        BigDecimal confirmAmount = resolveConfirmAmount(body, purchaseInvoice, payment);

        String methodCode;
        try {
            methodCode = PaymentMethodLabels.normalizeVendorMethod(
                    body != null ? body.getPaymentMethod() : null);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e.getMessage());
        }
        payment.setAmount(confirmAmount);
        payment.setPaymentMethod(methodCode);
        payment.setEffectiveDate(LocalDate.now());
        payment.setNotes(
                (payment.getNotes() == null ? "" : payment.getNotes() + " | ")
                        + "Vendor payment confirmed (AP)"
        );
        invoiceRepo.findByOrderIdAndType(po.getId(), InvoiceType.PURCHASE)
                .map(Invoice::getInvoiceId)
                .ifPresent(code -> {
                    if (payment.getInvoiceId() == null || payment.getInvoiceId().isBlank()) {
                        payment.setInvoiceId(code);
                    }
                });
        Payment saved = paymentRepo.save(payment);
        postVendorPaymentToAccounting(saved);
        Invoice updatedPurchaseInvoice = invoiceService.applyPayment(
                purchaseInvoice.getInvoiceId(), saved.getAmount());
        if ("PARTIALLY_PAID".equalsIgnoreCase(updatedPurchaseInvoice.getStatus())) {
            vendorPayableService.ensurePendingVendorPayableForOutstanding(
                    po, updatedPurchaseInvoice.getOutstanding());
        }
        try {
            invoiceService.regenerateGeneratedPurchaseInvoicePdfAfterVendorPayment(po.getId());
        } catch (Exception e) {
            log.warn(
                    "Failed to regenerate purchase invoice receipt PDF for PO id={}: {}",
                    po.getId(),
                    e.getMessage());
        }
        try {
            String purchaseInvoiceCode = invoiceRepo.findByOrderIdAndType(po.getId(), InvoiceType.PURCHASE)
                    .map(Invoice::getInvoiceId)
                    .orElse(null);
            Vendor supplier = po.getSupplier();
            String supplierName = supplier != null ? supplier.getVendorName() : null;
            String receiptUrl = vendorPaymentReceiptPdfService.generateAndUpload(
                    saved,
                    po.getCompany(),
                    po,
                    supplierName,
                    purchaseInvoiceCode);
            saved.setPdfUrl(receiptUrl);
            saved = paymentRepo.save(saved);
        } catch (Exception e) {
            log.warn(
                    "Failed to generate vendor payment receipt PDF for payment id={}: {}",
                    saved.getId(),
                    e.getMessage());
        }
        return toDTO(saved);
    }

    public String getOrCreateVendorPaymentReceiptPdfUrl(Long paymentId) {
        Payment payment = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));
        assertPaymentInTenant(payment);
        if (payment.getPaymentDirection() != PaymentDirection.VENDOR) {
            throw new RuntimeException("Receipt PDF is only available for vendor payments");
        }
        if (!"PENDING_VENDOR_PAYMENT".equalsIgnoreCase(payment.getPaymentMethod())) {
            String existing = payment.getPdfUrl();
            if (existing != null && !existing.isBlank() && !existing.contains("dummy.url")) {
                return existing;
            }
        } else {
            throw new RuntimeException("Confirm the vendor payment before downloading the receipt");
        }
        if (payment.getPurchaseOrderId() == null) {
            throw new RuntimeException("Vendor payment is not linked to a purchase order");
        }
        PurchaseOrder po = purchaseOrderRepo.findById(payment.getPurchaseOrderId())
                .orElseThrow(() -> new RuntimeException("Purchase order not found for vendor payment"));
        String purchaseInvoiceCode = invoiceRepo.findByOrderIdAndType(po.getId(), InvoiceType.PURCHASE)
                .map(Invoice::getInvoiceId)
                .orElse(null);
        Vendor supplier = po.getSupplier();
        String supplierName = supplier != null ? supplier.getVendorName() : null;
        String receiptUrl = vendorPaymentReceiptPdfService.generateAndUpload(
                payment,
                po.getCompany(),
                po,
                supplierName,
                purchaseInvoiceCode);
        payment.setPdfUrl(receiptUrl);
        paymentRepo.save(payment);
        return receiptUrl;
    }

    /**
     * Posts vendor payment to GL. When the PO has no prior encumbrance, accrues expense to AP first,
     * then settles AP using purchase defaults only (Dr purchase credit, Cr purchase debit).
     */
    private void postVendorPaymentToAccounting(Payment payment) {
        if (payment.getPurchaseOrderId() == null) {
            throw new RuntimeException("Vendor payment is not linked to a purchase order");
        }
        PurchaseOrder po = purchaseOrderRepo.findById(payment.getPurchaseOrderId())
                .orElseThrow(() -> new RuntimeException("Purchase order not found for vendor payment"));
        PurchaseRequisition pr = po.getSourceRequisition();
        Long companyId = payment.getCompany().getId();

        PurchasePostingAccountsResolver.ResolvedAccounts accounts =
                accountingDefaults.requirePurchaseAccounts(companyId);

        boolean encumbered = po.getFinanceTransactionId() != null
                || transactionService.hasPurchaseOrderEncumbrance(companyId, po.getId());

        if (!encumbered) {
            accountingDefaults.assertDistinctAccounts(
                    "Purchase encumbrance", accounts.debitAccountId(), accounts.creditAccountId());
            transactionService.createPurchaseOrderEncumbrance(
                    companyId,
                    po.getId(),
                    payment.getAmount(),
                    accounts.debitAccountId(),
                    accounts.creditAccountId(),
                    po.getOrderNumber());
        }

        Long debitAccountId = accounts.creditAccountId();
        Long creditAccountId = accounts.debitAccountId();
        accountingDefaults.assertDistinctAccounts(
                "Vendor payment posting", debitAccountId, creditAccountId);

        transactionService.validateTwoSidedPostingBalances(
                debitAccountId,
                creditAccountId,
                payment.getAmount(),
                companyId);

        String purchaseInvoiceCode = invoiceRepo.findByOrderIdAndType(po.getId(), InvoiceType.PURCHASE)
                .map(Invoice::getInvoiceId)
                .orElse(payment.getInvoiceId());

        TransactionResponseDTO tx = transactionService.create(CreateTransactionDTO.builder()
                .companyId(companyId)
                .transactionType(TransactionService.TYPE_VENDOR_PAYMENT)
                .transactionDate(payment.getEffectiveDate())
                .amount(payment.getAmount())
                .debitAccount(debitAccountId)
                .creditAccount(creditAccountId)
                .paymentId(String.valueOf(payment.getId()))
                .invoiceId(purchaseInvoiceCode)
                .relatedId(po.getId())
                .source(TransactionService.SOURCE_PURCHASE)
                .transactionDescription("Vendor payment (AP settlement) — PO " + po.getOrderNumber())
                .build());

        if (pr != null) {
            purchaseRequisitionRepo.findById(pr.getId()).ifPresent(managed -> {
                managed.setFinanceTransactionId(tx.getId());
                purchaseRequisitionRepo.save(managed);
            });
        }
    }

    /**
     * Posts customer (AR) payment to GL using sales defaults only (Dr sales debit, Cr sales credit).
     */
    private void postPaymentToAccounting(Payment payment, Invoice invoice) {
        if (payment.getId() == null || invoice == null) {
            return;
        }
        if (invoice.getOrderId() == null) {
            throw new RuntimeException("Unable to post payment: sales order reference is missing on invoice");
        }
        salesOrderRepo.findById(invoice.getOrderId())
                .orElseThrow(() -> new RuntimeException("Unable to post payment: sales order not found for invoice"));

        Long companyId = payment.getCompany().getId();
        var accounts = accountingDefaults.requireSalesAccounts(companyId);

        transactionService.createTransactionForPayment(
                payment.getId(),
                companyId,
                payment.getAmount(),
                accounts.debitAccountId(),
                accounts.creditAccountId(),
                payment.getEffectiveDate(),
                "PAYMENT",
                invoice.getInvoiceId()
        );
    }

    public void deletePayment(Long id) {
        Payment p = paymentRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found"));
        assertPaymentInTenant(p);
        paymentRepo.delete(p);
    }

    private boolean isSuperAdmin() {
        String r = auth.getCurrentUserRole();
        return r != null && "SUPER_ADMIN".equalsIgnoreCase(r);
    }

    private void assertTenantCompanyPath(Long requestedCompanyId) {
        if (requestedCompanyId == null) {
            throw new RuntimeException("Company is required");
        }
        if (isSuperAdmin()) {
            return;
        }
        Long current = auth.getCurrentCompanyId();
        if (current == null || !current.equals(requestedCompanyId)) {
            throw new RuntimeException("Access denied for this company");
        }
    }

    private void assertPaymentInTenant(Payment p) {
        if (isSuperAdmin()) {
            return;
        }
        Long cid = auth.getCurrentCompanyId();
        if (cid == null || p.getCompany() == null || !cid.equals(p.getCompany().getId())) {
            throw new RuntimeException("Payment not found or access denied");
        }
    }

    private void assertInvoiceCompany(Invoice inv) {
        if (isSuperAdmin()) {
            return;
        }
        Long cid = auth.getCurrentCompanyId();
        if (cid == null || inv.getCompany() == null || !cid.equals(inv.getCompany().getId())) {
            throw new RuntimeException("Invoice not found or access denied");
        }
    }
}
