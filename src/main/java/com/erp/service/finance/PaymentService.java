package com.erp.service.finance;

import com.erp.domain.finance.COAType;
import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.finance.Invoice;
import com.erp.domain.finance.Payment;
import com.erp.domain.finance.PaymentDirection;
import com.erp.domain.purchase.PurchaseOrder;
import com.erp.domain.purchase.PurchaseOrderStatus;
import com.erp.domain.purchase.PurchaseRequisition;
import com.erp.exception.ConflictException;
import com.erp.domain.sales.SalesOrder;
import com.erp.domain.hr.Company;
import com.erp.dto.finance.CreatePaymentDTO;
import com.erp.dto.finance.CreateTransactionDTO;
import com.erp.dto.finance.PaymentResponseDTO;
import com.erp.dto.finance.TransactionResponseDTO;
import com.erp.repo.finance.ChartOfAccountsRepository;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ChartOfAccountsRepository coaRepo;
    private final InvoiceRepository invoiceRepo;
    private final AuthContext auth;
    private final DocumentSequenceService documentSequenceService;
    private final VendorPaymentReceiptPdfService vendorPaymentReceiptPdfService;

    public PaymentService(PaymentRepository paymentRepo,
                          TransactionService transactionService,
                          InvoiceService invoiceService,
                          SalesOrderRepository salesOrderRepo,
                          CustomerEmailService customerEmailService,
                          CompanyRepository companyRepo,
                          PurchaseOrderRepository purchaseOrderRepo,
                          PurchaseRequisitionRepository purchaseRequisitionRepo,
                          ChartOfAccountsRepository coaRepo,
                          InvoiceRepository invoiceRepo,
                          AuthContext auth,
                          DocumentSequenceService documentSequenceService,
                          VendorPaymentReceiptPdfService vendorPaymentReceiptPdfService) {

        this.paymentRepo = paymentRepo;
        this.transactionService = transactionService;
        this.invoiceService = invoiceService;
        this.salesOrderRepo = salesOrderRepo;
        this.customerEmailService = customerEmailService;
        this.companyRepo = companyRepo;
        this.purchaseOrderRepo = purchaseOrderRepo;
        this.purchaseRequisitionRepo = purchaseRequisitionRepo;
        this.coaRepo = coaRepo;
        this.invoiceRepo = invoiceRepo;
        this.auth = auth;
        this.documentSequenceService = documentSequenceService;
        this.vendorPaymentReceiptPdfService = vendorPaymentReceiptPdfService;
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
        if (p.getInvoiceId() != null && !p.getInvoiceId().isBlank()) {
            invoiceRepo.findByInvoiceId(p.getInvoiceId()).ifPresent(inv -> {
                if (inv.getType() == InvoiceType.SALES && inv.getOrderId() != null) {
                    salesOrderRepo.findById(inv.getOrderId())
                            .ifPresent(so -> b.salesOrderNumber(so.getOrderNumber()));
                }
            });
        }
        return b.build();
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
    public PaymentResponseDTO confirmPayment(Long id) {
        Payment payment = paymentRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found"));
        assertPaymentInTenant(payment);

        PaymentDirection dir = payment.getPaymentDirection() != null
                ? payment.getPaymentDirection()
                : PaymentDirection.CUSTOMER;
        if (dir == PaymentDirection.VENDOR) {
            return confirmVendorPayment(payment);
        }

        if (!"PENDING_REQUEST".equalsIgnoreCase(payment.getPaymentMethod())) {
            throw new RuntimeException("Payment is already confirmed");
        }
        if (payment.getInvoiceId() == null || payment.getInvoiceId().isBlank()) {
            throw new RuntimeException("Invoice ID is missing for this payment request");
        }
        if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Invalid payment amount");
        }

        payment.setPaymentMethod("BANK_TRANSFER");
        payment.setEffectiveDate(LocalDate.now());
        payment.setNotes(
                (payment.getNotes() == null ? "" : payment.getNotes() + " | ")
                        + "Confirmed from payment request"
        );
        Payment saved = paymentRepo.save(payment);

        Invoice invoice = invoiceService.applyPayment(saved.getInvoiceId(), saved.getAmount());
        postPaymentToAccounting(saved, invoice);
        if ("PAID".equalsIgnoreCase(invoice.getStatus())) {
            salesOrderRepo.findById(invoice.getOrderId())
                    .ifPresent(order -> customerEmailService.sendReceiptEmail(order.getCustomer(), invoice));
        }
        return toDTO(saved);
    }

    private PaymentResponseDTO confirmVendorPayment(Payment payment) {
        if (!"PENDING_VENDOR_PAYMENT".equalsIgnoreCase(payment.getPaymentMethod())) {
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
        if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Invalid payment amount");
        }
        payment.setPaymentMethod("BANK_TRANSFER");
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
        invoiceService.applyPurchaseInvoicePaymentForPurchaseOrder(
                saved.getPurchaseOrderId(), saved.getAmount());
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
     * Posts vendor payment to the GL. When the PO was already released with an encumbrance
     * (debit/credit purchase defaults), pays down the credit (AP) account and credits cash.
     * Otherwise debits expense/inventory from the PR and credits cash (legacy path).
     */
    private void postVendorPaymentToAccounting(Payment payment) {
        if (payment.getPurchaseOrderId() == null) {
            throw new RuntimeException("Vendor payment is not linked to a purchase order");
        }
        PurchaseOrder po = purchaseOrderRepo.findById(payment.getPurchaseOrderId())
                .orElseThrow(() -> new RuntimeException("Purchase order not found for vendor payment"));
        PurchaseRequisition pr = po.getSourceRequisition();
        Long cashAccountId = resolveDefaultCashAccountId(payment.getCompany().getId());
        Long companyId = payment.getCompany().getId();

        Long debitAccountId;
        Long creditAccountId;
        boolean encumbered = po.getFinanceTransactionId() != null
                || transactionService.hasPurchaseOrderEncumbrance(po.getId());

        if (encumbered) {
            if (pr == null || pr.getCreditAccount() == null) {
                Company company = po.getCompany();
                Long apId = company.getDefaultPurchaseCreditAccountId();
                if (apId == null) {
                    throw new RuntimeException(
                            "Cannot post vendor payment: purchase order has encumbrance but no credit (AP) account is available.");
                }
                debitAccountId = apId;
            } else {
                debitAccountId = pr.getCreditAccount().getId();
            }
            creditAccountId = cashAccountId;
        } else {
            if (pr == null || pr.getDebitAccount() == null) {
                throw new RuntimeException(
                        "Cannot post vendor payment to the chart of accounts: this purchase order has no source "
                                + "requisition with a debit (expense/inventory) account. Create the PO from an approved PR.");
            }
            debitAccountId = pr.getDebitAccount().getId();
            creditAccountId = cashAccountId;
        }

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
                .transactionDescription(
                        encumbered
                                ? "Vendor payment (AP settlement) — PO " + po.getOrderNumber()
                                : "Vendor payment (AP) — PO " + po.getOrderNumber())
                .build());

        if (pr != null) {
            purchaseRequisitionRepo.findById(pr.getId()).ifPresent(managed -> {
                managed.setFinanceTransactionId(tx.getId());
                purchaseRequisitionRepo.save(managed);
            });
        }
    }

    private Long resolveDefaultCashAccountId(Long companyId) {
        List<ChartOfAccounts> list = coaRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
        return list.stream()
                .filter(a -> a.getType() == COAType.CASH)
                .findFirst()
                .or(() -> list.stream().filter(a -> a.getType() == COAType.ASSET).findFirst())
                .map(ChartOfAccounts::getId)
                .orElseThrow(() -> new RuntimeException(
                        "Add a CASH (or ASSET) chart-of-accounts account for this company to post vendor payments."));
    }

    private void postPaymentToAccounting(Payment payment, Invoice invoice) {
        if (payment.getId() == null || invoice == null) {
            return;
        }
        if (invoice.getOrderId() == null) {
            throw new RuntimeException("Unable to post payment: sales order reference is missing on invoice");
        }
        SalesOrder salesOrder = salesOrderRepo.findById(invoice.getOrderId())
                .orElseThrow(() -> new RuntimeException("Unable to post payment: sales order not found for invoice"));

        Long companyId = payment.getCompany().getId();
        Long debitAccountId = salesOrder.getDebitAccount() != null ? salesOrder.getDebitAccount().getId() : null;
        Long creditAccountId = salesOrder.getCreditAccount() != null ? salesOrder.getCreditAccount().getId() : null;
        if (debitAccountId == null) {
            throw new RuntimeException("Unable to post payment: debit account is missing on sales order");
        }
        if (creditAccountId == null) {
            throw new RuntimeException("Unable to post payment: credit account is missing on sales order");
        }

        transactionService.createTransactionForPayment(
                payment.getId(),
                companyId,
                payment.getAmount(),
                debitAccountId,
                creditAccountId,
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
