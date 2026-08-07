package com.erp.service.finance;

import com.erp.domain.InvoiceType;
import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.finance.Invoice;
import com.erp.domain.finance.InvoiceDocumentSource;
import com.erp.domain.finance.Payment;
import com.erp.domain.finance.PaymentDirection;
import com.erp.domain.finance.Transaction;
import com.erp.domain.hr.BankAccount;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.CompanyInvoiceSettings;
import com.erp.domain.inventory.Customer;
import com.erp.dto.file.FileCategory;
import com.erp.dto.file.FileUploadResult;
import com.erp.dto.finance.InvoiceRequest;
import com.erp.dto.finance.InvoiceResponse;
import com.erp.dto.purchase.PurchaseOrderResponseDTO;
import com.erp.dto.sales.SalesOrderResponseDTO;
import com.erp.domain.purchase.PurchaseOrder;
import com.erp.domain.purchase.PurchaseOrderStatus;
import com.erp.domain.purchase.PurchaseRequisition;
import com.erp.repo.finance.ChartOfAccountsRepository;
import com.erp.repo.finance.InvoiceRepository;
import com.erp.repo.finance.PaymentRepository;
import com.erp.repo.finance.TransactionRepository;
import com.erp.repo.hr.BankAccountRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.hr.CompanyInvoiceSettingsRepository;
import com.erp.repo.purchase.PurchaseOrderRepository;
import com.erp.repo.sales.SalesOrderRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.file.FileStorageService;
import com.erp.service.notification.CustomerEmailService;
import com.erp.service.pdf.InvoicePDFService;
import com.erp.service.purchase.PurchaseOrderService;
import com.erp.service.sales.SalesOrderService;
import com.erp.service.hr.InvoiceSettingsDefaults;
import com.erp.service.DocumentSequenceService;
import com.erp.util.InMemoryMultipartFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {
    @Value("${app.public-base-url:http://localhost:5173}")
    private String publicBaseUrl;

    private final InvoiceRepository repo;
    private final CompanyRepository companyRepo;
    private final CompanyInvoiceSettingsRepository invoiceSettingsRepo;
    private final ChartOfAccountsRepository coaRepo;
    private final BankAccountRepository bankAccountRepo;
    private final SalesOrderRepository salesOrderRepo;
    private final PaymentRepository paymentRepo;
    private final TransactionRepository transactionRepo;
    private final InvoicePDFService pdfService;
    private final FileStorageService fileStorageService;
    private final AuthContext auth;
    private final SalesOrderService salesOrderService;
    private final PurchaseOrderService purchaseOrderService;
    private final PurchaseOrderRepository purchaseOrderRepo;
    private final CustomerEmailService customerEmailService;
    private final TransactionService transactionService;
    private final CompanyAccountingDefaultsService accountingDefaults;
    private final DocumentSequenceService documentSequenceService;

    @Transactional
    public void handleSalesOrderCancellation(Long salesOrderId) {
        if (salesOrderId == null) {
            return;
        }

        Optional<Invoice> invoiceOpt = repo.findByOrderIdAndType(salesOrderId, InvoiceType.SALES);
        if (invoiceOpt.isEmpty()) {
            return;
        }

        Invoice invoice = invoiceOpt.get();
        if ("CANCELLED".equalsIgnoreCase(invoice.getStatus())) {
            return;
        }

        Long companyId = invoice.getCompany() != null ? invoice.getCompany().getId() : null;
        List<Payment> payments = companyId == null
                ? List.of()
                : paymentRepo.findByCompany_IdAndInvoiceIdOrderByCreatedAtDesc(companyId, invoice.getInvoiceId());
        for (Payment payment : payments) {
            String paymentId = payment.getId() != null ? String.valueOf(payment.getId()) : null;
            if (paymentId != null) {
                List<Transaction> reversals =
                        transactionRepo.findByPaymentIdAndTransactionTypeOrderByCreatedAtDesc(paymentId, "PAYMENT_REVERSAL");
                if (reversals.isEmpty()) {
                    List<Transaction> originalTxns = transactionRepo.findByPaymentIdOrderByCreatedAtDesc(paymentId);
                    for (Transaction tx : originalTxns) {
                        if ("PAYMENT_REVERSAL".equalsIgnoreCase(tx.getTransactionType())) {
                            continue;
                        }
                        if (tx.getDebitAccount() == null || tx.getCreditAccount() == null) {
                            continue;
                        }
                        transactionService.createTransactionForPayment(
                                payment.getId(),
                                payment.getCompany().getId(),
                                tx.getAmount(),
                                tx.getCreditAccount().getId(),
                                tx.getDebitAccount().getId(),
                                LocalDate.now(),
                                "PAYMENT_REVERSAL",
                                tx.getInvoiceId() != null ? tx.getInvoiceId() : invoice.getInvoiceId());
                    }
                }
            }

            if ("PENDING_REQUEST".equalsIgnoreCase(payment.getPaymentMethod())) {
                payment.setPaymentMethod("CANCELLED");
            }
            payment.setNotes(
                    (payment.getNotes() == null ? "" : payment.getNotes() + " | ")
                            + "Sales order cancelled; accounting reversed where applicable");
            paymentRepo.save(payment);
        }

        // Ensure cancellation is reflected in transactions/COA even when no payment transaction exists yet.
        if (invoice.getDebitAccount() != null
                && invoice.getCreditAccount() != null
                && invoice.getAmount() != null
                && invoice.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            try {
                transactionService.createSalesOrderCancelReversal(
                        invoice.getCompany().getId(),
                        salesOrderId,
                        invoice.getAmount(),
                        invoice.getDebitAccount().getId(),
                        invoice.getCreditAccount().getId(),
                        invoice.getInvoiceId());
            } catch (ResponseStatusException ignored) {
                // If reversal already exists, keep cancellation idempotent.
            }
        }

        invoice.setStatus("CANCELLED");
        invoice.setOpenAmount(BigDecimal.ZERO);
        invoice.setOutstanding(BigDecimal.ZERO);
        invoice.setNotesRemarks(
                (invoice.getNotesRemarks() == null ? "" : invoice.getNotesRemarks() + " | ")
                        + "Cancelled from sales order cancellation");
        repo.save(invoice);
    }

    // ============================================================
    // GET OR CREATE PDF (IDEMPOTENT)
    // ============================================================
    public String getOrCreateInvoicePdfUrl(Long invoiceId) {

        Invoice invoice = repo.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        assertInvoiceInTenant(invoice);

        if (invoice.getDocumentSource() == InvoiceDocumentSource.SUPPLIER_UPLOAD
                && invoice.getPdfUrl() != null && !invoice.getPdfUrl().isBlank()) {
            return invoice.getPdfUrl();
        }
        if (invoice.getDocumentSource() == InvoiceDocumentSource.EXTERNAL_LINK) {
            if (invoice.getPdfUrl() != null && !invoice.getPdfUrl().isBlank()) {
                return invoice.getPdfUrl();
            }
            if (invoice.getExternalDocumentUrl() != null && !invoice.getExternalDocumentUrl().isBlank()) {
                return invoice.getExternalDocumentUrl();
            }
        }

        // Fully paid → receipt PDF; otherwise keep/generate the original invoice PDF.
        if (isFullyPaid(invoice)) {
            return getOrCreateReceiptPdfUrl(invoice);
        }
        return getOrCreateOriginalInvoicePdfUrl(invoice);
    }

    private boolean isFullyPaid(Invoice invoice) {
        return "PAID".equalsIgnoreCase(invoice.getStatus() == null ? "" : invoice.getStatus().trim());
    }

    /** Original pre-payment invoice document for GENERATED invoices (rebuilt so totals stay current). */
    private String getOrCreateOriginalInvoicePdfUrl(Invoice invoice) {
        // Always rebuild system-generated PDFs so template/total fixes apply immediately.
        // Supplier uploads and external links keep their stored URL.
        if (invoice.getDocumentSource() == null
                || invoice.getDocumentSource() == InvoiceDocumentSource.GENERATED) {
            return generateAndUploadInvoicePdf(invoice, false);
        }
        if (invoice.getPdfUrl() != null && !invoice.getPdfUrl().isBlank()) {
            return invoice.getPdfUrl();
        }
        return generateAndUploadInvoicePdf(invoice, false);
    }

    /** Post-payment receipt document. */
    private String getOrCreateReceiptPdfUrl(Invoice invoice) {
        if (invoice.getReceiptPdfUrl() != null && !invoice.getReceiptPdfUrl().isBlank()) {
            return invoice.getReceiptPdfUrl();
        }
        return generateAndUploadInvoicePdf(invoice, true);
    }

    // ============================================================
    // CREATE MANUAL INVOICE
    // ============================================================
    public InvoiceResponse createInvoice(InvoiceRequest req) {
        return createInvoice(req, null, false);
    }

    public InvoiceResponse createInvoice(InvoiceRequest req, MultipartFile supplierDocument) {
        return createInvoice(req, supplierDocument, false);
    }

    private InvoiceResponse createInvoice(
            InvoiceRequest req,
            MultipartFile supplierDocument,
            boolean allowSystemSalesInvoiceCreation
    ) {
        if (req.getType() == InvoiceType.SALES && !allowSystemSalesInvoiceCreation) {
            throw new RuntimeException(
                    "Manual SALES invoice creation is disabled. Sales invoices are auto-created from order confirmation.");
        }

        Company company = companyRepo.findById(auth.getCurrentCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found"));

        assertNoDuplicateInvoice(company, req);

        InvoiceDocumentSource docSource = req.getDocumentSource() != null
                ? req.getDocumentSource()
                : InvoiceDocumentSource.GENERATED;

        if (docSource == InvoiceDocumentSource.EXTERNAL_LINK
                && (req.getExternalDocumentUrl() == null || req.getExternalDocumentUrl().isBlank())) {
            throw new RuntimeException("externalDocumentUrl is required when documentSource is EXTERNAL_LINK");
        }

        ChartOfAccounts debitAccount = coaRepo.findById(req.getDebitAccount())
                .orElseThrow(() -> new RuntimeException("Debit Account not found"));

        ChartOfAccounts creditAccount = coaRepo.findById(req.getCreditAccount())
                .orElseThrow(() -> new RuntimeException("Credit Account not found"));

        Long bankId = req.getBankAccountId();
        if (bankId == null && company.getDefaultBankAccountId() != null
                && req.getType() != InvoiceType.PURCHASE) {
            bankId = company.getDefaultBankAccountId();
        }
        BankAccount bankAccount = bankId == null
                ? null
                : bankAccountRepo.findById(bankId)
                .filter(b -> b.getCompany().getId().equals(company.getId()))
                .orElseThrow(() -> new RuntimeException("Bank account not found"));

        BigDecimal amount = req.getAmount() != null ? req.getAmount() : BigDecimal.ZERO;
        BigDecimal subtotal = req.getSubtotalAmount() != null ? req.getSubtotalAmount() : amount;
        BigDecimal discount = req.getDiscountAmount() != null ? req.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal tax = req.getTaxAmount() != null ? req.getTaxAmount() : BigDecimal.ZERO;

        String supplierRef = req.getSupplierInvoiceNumber() != null
                ? req.getSupplierInvoiceNumber().trim()
                : null;
        if (supplierRef != null && supplierRef.isEmpty()) {
            supplierRef = null;
        }

        String invoiceCode = documentSequenceService.generateNext("INV");

        Invoice invoice = Invoice.builder()
                .invoiceId(invoiceCode)
                .company(company)
                .toParty(req.getToParty())
                .invoiceDate(req.getInvoiceDate() == null ? LocalDate.now() : req.getInvoiceDate())
                .dueDate(req.getDueDate())
                .status("UNPAID")
                .amount(amount)
                .subtotalAmount(subtotal)
                .discountAmount(discount)
                .taxAmount(tax)
                .openAmount(amount)
                .outstanding(amount)
                .itemDescription(req.getItemDescription())
                .notesRemarks(req.getNotesRemarks())
                .gracePeriod(req.getGracePeriod())
                .interestRate(req.getInterestRate())
                .partyClassification(req.getPartyClassification())
                .createdAt(Instant.now())
                .type(req.getType())
                .orderId(req.getOrderId())
                .debitAccount(debitAccount)
                .creditAccount(creditAccount)
                .bankAccount(bankAccount)
                .documentSource(docSource)
                .supplierInvoiceNumber(supplierRef)
                .externalDocumentUrl(
                        req.getExternalDocumentUrl() != null && !req.getExternalDocumentUrl().isBlank()
                                ? req.getExternalDocumentUrl().trim()
                                : null
                )
                .build();

        applyExternalLinkPdfHint(invoice);

        Invoice saved = repo.save(invoice);

        if (req.getType() == InvoiceType.SALES) {
            createPendingPaymentEntry(saved);
        }

        finalizeInvoiceDocument(saved, supplierDocument);

        if (req.getType() == InvoiceType.SALES && req.getOrderId() != null) {
            Customer customer = salesOrderRepo.findById(req.getOrderId())
                    .map(so -> so.getCustomer())
                    .orElse(null);
            customerEmailService.sendInvoiceCreatedEmail(customer, saved);
        }

        return toDTO(repo.findById(saved.getId()).orElse(saved));
    }

    private void assertNoDuplicateInvoice(Company company, InvoiceRequest req) {
        if (req.getOrderId() == null) {
            return;
        }
        Long orderId = req.getOrderId();
        if (req.getType() == InvoiceType.SALES) {
            if (repo.findByOrderIdAndType(orderId, InvoiceType.SALES).isPresent()) {
                throw new RuntimeException("Invoice already exists for selected order");
            }
            return;
        }
        if (req.getType() == InvoiceType.PURCHASE) {
            Optional<Invoice> dup = repo.findByOrderIdAndType(orderId, InvoiceType.PURCHASE);
            if (dup.isPresent() && dup.get().getCompany().getId().equals(company.getId())) {
                throw new RuntimeException("Invoice already exists for this purchase order");
            }
        }
    }

    /**
     * If external URL looks like a direct PDF, mirror into pdfUrl so clients can embed-preview.
     */
    private void applyExternalLinkPdfHint(Invoice invoice) {
        if (invoice.getDocumentSource() != InvoiceDocumentSource.EXTERNAL_LINK) {
            return;
        }
        String url = invoice.getExternalDocumentUrl();
        if (url == null) {
            return;
        }
        String lower = url.toLowerCase();
        if (lower.contains(".pdf") && (lower.endsWith(".pdf") || lower.contains(".pdf?"))) {
            invoice.setPdfUrl(url);
        }
    }

    private void finalizeInvoiceDocument(Invoice saved, MultipartFile supplierDocument) {
        InvoiceDocumentSource docSource = saved.getDocumentSource();

        if (docSource == InvoiceDocumentSource.SUPPLIER_UPLOAD) {
            if (supplierDocument != null && !supplierDocument.isEmpty()) {
                uploadSupplierPdfAndPersist(saved, supplierDocument);
            }
            return;
        }
        if (docSource == InvoiceDocumentSource.EXTERNAL_LINK) {
            repo.save(saved);
            return;
        }

        // GENERATED
        if (saved.getType() == InvoiceType.PURCHASE && saved.getOrderId() == null) {
            throw new RuntimeException("Generated purchase invoices require a purchase order (orderId)");
        }
        Invoice refreshed = repo.findById(saved.getId()).orElse(saved);
        try {
            generateAndUploadInvoicePdf(refreshed, false);
        } catch (Exception e) {
            // Invoice row must remain even when PDF rendering/upload fails.
            log.error(
                    "Invoice created but PDF generation failed for invoiceId={}: {}",
                    refreshed.getInvoiceId(),
                    e.getMessage(),
                    e);
        }
    }

    public InvoiceResponse matchVendorInvoice(Long invoiceId, String vendorInvoiceNumber, MultipartFile file) {
        if (vendorInvoiceNumber == null || vendorInvoiceNumber.isBlank()) {
            throw new RuntimeException("Vendor invoice number is required");
        }
        Invoice inv = repo.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        assertInvoiceInTenant(inv);
        if (inv.getType() != InvoiceType.PURCHASE) {
            throw new RuntimeException("Vendor invoice matching applies only to purchase invoices");
        }
        try {
            inv.setSupplierInvoiceNumber(vendorInvoiceNumber.trim());
            if (file != null && !file.isEmpty()) {
                FileUploadResult uploadResult = fileStorageService.upload(
                        file,
                        FileCategory.VENDOR_INVOICE_MATCH_DOCUMENT,
                        inv.getId().toString(),
                        true,
                        inv.getCompany().getId()
                );
                inv.setVendorInvoiceDocumentUrl(fileStorageService.getPublicUrl(uploadResult.getBlobPath()));
            }
            inv.setVendorInvoiceMatchedAt(Instant.now());
            repo.save(inv);
        } catch (Exception e) {
            throw new RuntimeException("Vendor invoice document upload failed", e);
        }
        return toDTO(repo.findById(inv.getId()).orElse(inv));
    }

    public InvoiceResponse attachSupplierDocument(Long invoiceId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File is required");
        }
        Invoice inv = repo.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        assertInvoiceInTenant(inv);
        if (inv.getType() != InvoiceType.PURCHASE) {
            throw new RuntimeException("Supplier documents apply only to purchase invoices");
        }
        inv.setDocumentSource(InvoiceDocumentSource.SUPPLIER_UPLOAD);
        uploadSupplierPdfAndPersist(inv, file);
        return toDTO(repo.findById(inv.getId()).orElse(inv));
    }

    private void uploadSupplierPdfAndPersist(Invoice invoice, MultipartFile file) {
        try {
            FileUploadResult uploadResult = fileStorageService.upload(
                    file,
                    FileCategory.INVOICE_PDF,
                    invoice.getId().toString(),
                    true,
                    invoice.getCompany().getId()
            );
            String pdfUrl = fileStorageService.getPublicUrl(uploadResult.getBlobPath());
            invoice.setPdfUrl(pdfUrl);
            invoice.setDocumentSource(InvoiceDocumentSource.SUPPLIER_UPLOAD);
            repo.save(invoice);
        } catch (Exception e) {
            throw new RuntimeException("Supplier document upload failed", e);
        }
    }

    private void createPendingPaymentEntry(Invoice invoice) {
        ensurePendingPaymentRequestForOutstanding(invoice);
    }

    /**
     * Creates a pending customer payment request for the invoice's remaining outstanding balance
     * when none already exists (e.g. after a partial payment confirmation).
     */
    @Transactional
    public void ensurePendingPaymentRequestForOutstanding(Invoice invoice) {
        if (invoice == null || invoice.getInvoiceId() == null || invoice.getInvoiceId().isBlank()) {
            return;
        }
        String status = invoice.getStatus() == null ? "" : invoice.getStatus().trim();
        if ("PAID".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status)) {
            return;
        }
        Long companyId = invoice.getCompany() != null ? invoice.getCompany().getId() : null;
        if (companyId == null
                || paymentRepo.existsByCompany_IdAndInvoiceIdAndPaymentMethod(
                        companyId, invoice.getInvoiceId(), "PENDING_REQUEST")) {
            return;
        }
        BigDecimal outstanding = invoice.getOutstanding() != null
                ? invoice.getOutstanding()
                : invoice.getAmount();
        if (outstanding == null || outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Payment payment = Payment.builder()
                .paymentCode(documentSequenceService.generateNext("PAY-REQ"))
                .company(invoice.getCompany())
                .paymentMethod("PENDING_REQUEST")
                .amount(outstanding)
                .effectiveDate(invoice.getDueDate())
                .notes("Payment request for invoice " + invoice.getInvoiceId())
                .invoiceId(invoice.getInvoiceId())
                .paymentDirection(PaymentDirection.CUSTOMER)
                .createdBy(auth.getCurrentUserId())
                .build();
        paymentRepo.save(payment);
    }

    public InvoiceResponse createInvoiceForConfirmedSalesOrder(Long salesOrderId) {
        var order = salesOrderRepo.findById(salesOrderId)
                .orElseThrow(() -> new RuntimeException("Sales order not found"));
        if (repo.findByOrderIdAndType(order.getId(), InvoiceType.SALES).isPresent()) {
            throw new RuntimeException("Invoice already exists for this sales order");
        }
        Long companyId = order.getCompany().getId();
        var salesAccounts = accountingDefaults.requireSalesAccounts(companyId);

        if (order.getBankAccount() == null) {
            throw new RuntimeException("Sales order is missing bank account");
        }
        if (order.getInvoiceDueDate() == null) {
            throw new RuntimeException("Sales order is missing invoice due date");
        }

        InvoiceRequest req = new InvoiceRequest();
        req.setType(InvoiceType.SALES);
        req.setOrderId(order.getId());
        req.setToParty(order.getCustomer().getCustomerName());
        req.setInvoiceDate(LocalDate.now());
        req.setDueDate(order.getInvoiceDueDate());
        req.setAmount(order.getTotalAmount());
        req.setDebitAccount(salesAccounts.debitAccountId());
        req.setCreditAccount(salesAccounts.creditAccountId());
        req.setBankAccountId(order.getBankAccount().getId());
        req.setItemDescription("Auto-generated from sales order " + order.getOrderNumber());
        req.setNotesRemarks("Invoice created on sales order confirmation.");
        InvoiceResponse response = createInvoice(req, null, true);
        Invoice saved = repo.findById(response.getId())
                .orElseThrow(() -> new RuntimeException("Invoice not found after creation"));
        saved.setSubtotalAmount(order.getSubtotalAmount() == null ? BigDecimal.ZERO : order.getSubtotalAmount());
        saved.setDiscountAmount(order.getDiscountAmount() == null ? BigDecimal.ZERO : order.getDiscountAmount());
        saved.setTaxAmount(order.getTaxAmount() == null ? BigDecimal.ZERO : order.getTaxAmount());
        saved.setAmount(order.getTotalAmount());
        saved.setOpenAmount(order.getTotalAmount());
        saved.setOutstanding(order.getTotalAmount());
        return toDTO(repo.save(saved));
    }

    /**
     * Creates a generated purchase (AP) cross-check invoice after the PO is released to the supplier.
     * Idempotent: returns the existing invoice if one already exists.
     * Runs in a new transaction when invoked after PO commit so PDF generation sees a persisted PO.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public InvoiceResponse createOrGetGeneratedPurchaseInvoiceForPurchaseOrder(Long purchaseOrderId) {
        Optional<Invoice> existing = repo.findByOrderIdAndType(purchaseOrderId, InvoiceType.PURCHASE);
        if (existing.isPresent()) {
            linkPurchaseInvoiceToFinanceReferences(purchaseOrderId, existing.get().getInvoiceId());
            return toDTO(existing.get());
        }

        PurchaseOrder po = purchaseOrderRepo.findById(purchaseOrderId)
                .filter(p -> p.getCompany().getId().equals(auth.getCurrentCompanyId()))
                .orElseThrow(() -> new RuntimeException("Purchase order not found"));

        if (po.getStatus() == PurchaseOrderStatus.DRAFT || po.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new RuntimeException(
                    "Purchase invoice is created when the purchase order is released to the supplier (confirmed).");
        }
        if (po.getSupplier() == null) {
            throw new RuntimeException("Assign a supplier before generating the purchase invoice");
        }

        Long companyId = po.getCompany().getId();
        var purchaseAccounts = accountingDefaults.requirePurchaseAccounts(companyId);
        accountingDefaults.assertDistinctAccounts(
                "Purchase invoice posting",
                purchaseAccounts.debitAccountId(),
                purchaseAccounts.creditAccountId());

        BigDecimal total = po.getTotalAmount() != null ? po.getTotalAmount() : BigDecimal.ZERO;

        InvoiceRequest req = new InvoiceRequest();
        req.setType(InvoiceType.PURCHASE);
        req.setOrderId(po.getId());
        req.setToParty(po.getSupplier().getVendorName());
        req.setInvoiceDate(LocalDate.now());
        LocalDate baseDate = po.getOrderDate() != null ? po.getOrderDate() : LocalDate.now();
        req.setDueDate(baseDate.plusDays(30));
        req.setAmount(total);
        req.setSubtotalAmount(total);
        req.setDiscountAmount(BigDecimal.ZERO);
        req.setTaxAmount(BigDecimal.ZERO);
        req.setDebitAccount(purchaseAccounts.debitAccountId());
        req.setCreditAccount(purchaseAccounts.creditAccountId());
        req.setItemDescription(resolvePurchaseInvoiceDescription(po));
        req.setNotesRemarks(resolvePurchaseInvoiceNotes(po));
        req.setDocumentSource(InvoiceDocumentSource.GENERATED);

        InvoiceResponse response = createInvoice(req, null, false);
        Invoice saved = repo.findById(response.getId())
                .orElseThrow(() -> new RuntimeException("Invoice not found after creation"));
        saved.setSubtotalAmount(total);
        saved.setDiscountAmount(BigDecimal.ZERO);
        saved.setTaxAmount(BigDecimal.ZERO);
        saved.setAmount(total);
        saved.setOpenAmount(total);
        saved.setOutstanding(total);
        Invoice persisted = repo.save(saved);
        linkPurchaseInvoiceToFinanceReferences(purchaseOrderId, persisted.getInvoiceId());
        return toDTO(persisted);
    }

    /**
     * Reduces a purchase order's system-generated (cross-check) invoice when goods are
     * rejected at inspection and the invoice has not yet been fully settled. Only applies
     * to {@link InvoiceDocumentSource#GENERATED} invoices, priced purely off {@code po.totalAmount};
     * manually-entered supplier invoices (independent tax/discount amounts) are left untouched.
     * No-op (with a warning log) when no generated purchase invoice exists for the PO.
     */
    @Transactional
    public void reduceForRejectedGoods(Long purchaseOrderId, BigDecimal rejectedAmount, String reason) {
        if (rejectedAmount == null || rejectedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Optional<Invoice> existing = repo.findByOrderIdAndType(purchaseOrderId, InvoiceType.PURCHASE);
        if (existing.isEmpty() || existing.get().getDocumentSource() != InvoiceDocumentSource.GENERATED) {
            log.warn("No generated purchase invoice found for PO {} to reduce for rejected goods ({}); {}",
                    purchaseOrderId, rejectedAmount, reason);
            return;
        }

        Invoice invoice = existing.get();
        BigDecimal amount = clampNonNegative(nullToZero(invoice.getAmount()).subtract(rejectedAmount));
        BigDecimal subtotal = clampNonNegative(nullToZero(invoice.getSubtotalAmount()).subtract(rejectedAmount));
        BigDecimal outstanding = clampNonNegative(nullToZero(invoice.getOutstanding()).subtract(rejectedAmount));
        BigDecimal openAmount = clampNonNegative(nullToZero(invoice.getOpenAmount()).subtract(rejectedAmount));

        invoice.setAmount(amount);
        invoice.setSubtotalAmount(subtotal);
        invoice.setOutstanding(outstanding);
        invoice.setOpenAmount(openAmount);
        if (outstanding.compareTo(BigDecimal.ZERO) == 0) {
            invoice.setStatus("ADJUSTED");
        }
        repo.save(invoice);
    }

    /**
     * Reduces a sales invoice when the customer returns goods and the invoice is not yet fully paid.
     * Mirrors {@link #reduceForRejectedGoods} for the AR side.
     */
    @Transactional
    public void reduceForReturnedSalesGoods(Long salesOrderId, BigDecimal returnedAmount, String reason) {
        if (returnedAmount == null || returnedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Optional<Invoice> existing = repo.findByOrderIdAndType(salesOrderId, InvoiceType.SALES);
        if (existing.isEmpty()) {
            log.warn("No sales invoice found for SO {} to reduce for returned goods ({}); {}",
                    salesOrderId, returnedAmount, reason);
            return;
        }

        Invoice invoice = existing.get();
        BigDecimal amount = clampNonNegative(nullToZero(invoice.getAmount()).subtract(returnedAmount));
        BigDecimal subtotal = clampNonNegative(nullToZero(invoice.getSubtotalAmount()).subtract(returnedAmount));
        BigDecimal outstanding = clampNonNegative(nullToZero(invoice.getOutstanding()).subtract(returnedAmount));
        BigDecimal openAmount = clampNonNegative(nullToZero(invoice.getOpenAmount()).subtract(returnedAmount));

        invoice.setAmount(amount);
        invoice.setSubtotalAmount(subtotal);
        invoice.setOutstanding(outstanding);
        invoice.setOpenAmount(openAmount);
        if (outstanding.compareTo(BigDecimal.ZERO) == 0) {
            invoice.setStatus("ADJUSTED");
        }
        repo.save(invoice);
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static BigDecimal clampNonNegative(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : value;
    }

    private String resolvePurchaseInvoiceDescription(PurchaseOrder po) {
        PurchaseRequisition source = po.getSourceRequisition();
        if (source != null) {
            String description = trimToNull(source.getRequisitionDescription());
            if (description != null) {
                return description;
            }
        }
        return "Auto-generated from purchase order " + po.getOrderNumber();
    }

    private String resolvePurchaseInvoiceNotes(PurchaseOrder po) {
        PurchaseRequisition source = po.getSourceRequisition();
        if (source != null) {
            String justification = trimToNull(source.getJustification());
            if (justification != null) {
                return justification;
            }
        }
        return "Auto-generated when purchase order was released to supplier (internal cross-check for AP matching).";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void linkPurchaseInvoiceToFinanceReferences(Long purchaseOrderId, String invoiceCode) {
        if (purchaseOrderId == null || invoiceCode == null || invoiceCode.isBlank()) {
            return;
        }
        transactionService.linkInvoiceToPurchaseOrderTransactions(purchaseOrderId, invoiceCode);
        paymentRepo.findFirstByPurchaseOrderIdAndPaymentDirection(
                        purchaseOrderId, PaymentDirection.VENDOR)
                .ifPresent(payment -> {
                    if (payment.getInvoiceId() == null || payment.getInvoiceId().isBlank()) {
                        payment.setInvoiceId(invoiceCode);
                        paymentRepo.save(payment);
                    }
                });
    }

    // ============================================================
    // APPLY PAYMENT
    // ============================================================
    public Invoice applyPayment(String invoiceId, BigDecimal amount) {
        Long companyId = auth.getCurrentCompanyId();
        if (companyId == null) {
            throw new RuntimeException("Invoice not found");
        }
        return applyPayment(companyId, invoiceId, amount);
    }

    public Invoice applyPayment(Long companyId, String invoiceId, BigDecimal amount) {
        if (companyId == null || invoiceId == null || invoiceId.isBlank()) {
            throw new RuntimeException("Invoice not found");
        }
        Invoice inv = repo.findByCompany_IdAndInvoiceId(companyId, invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        if ("CANCELLED".equalsIgnoreCase(inv.getStatus())) {
            throw new RuntimeException("Cannot apply payment to a cancelled invoice");
        }

        BigDecimal outstanding = inv.getOutstanding() != null ? inv.getOutstanding() : inv.getAmount();
        if (outstanding == null) {
            outstanding = BigDecimal.ZERO;
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Payment amount must be greater than zero");
        }
        if (amount.compareTo(outstanding) > 0) {
            throw new RuntimeException("Payment amount cannot exceed outstanding balance");
        }

        BigDecimal newOutstanding = outstanding.subtract(amount);

        if (newOutstanding.compareTo(BigDecimal.ZERO) <= 0) {
            inv.setOutstanding(BigDecimal.ZERO);
            inv.setOpenAmount(BigDecimal.ZERO);
            inv.setStatus("PAID");
            inv.setPaidDate(LocalDate.now());
        } else {
            inv.setOutstanding(newOutstanding);
            inv.setOpenAmount(newOutstanding);
            inv.setStatus("PARTIALLY_PAID");
        }

        Invoice saved = repo.save(inv);
        completeLinkedSalesOrderWhenPaid(saved);
        if (isFullyPaid(saved) && saved.getDocumentSource() == InvoiceDocumentSource.GENERATED) {
            try {
                generateAndUploadInvoicePdf(saved, true);
            } catch (Exception e) {
                log.warn(
                        "Failed to generate receipt PDF for invoice {}: {}",
                        saved.getInvoiceId(),
                        e.getMessage());
            }
        }
        return saved;
    }

    private void completeLinkedSalesOrderWhenPaid(Invoice invoice) {
        if (invoice.getType() != InvoiceType.SALES || invoice.getOrderId() == null) {
            return;
        }
        if (!"PAID".equalsIgnoreCase(invoice.getStatus())) {
            return;
        }
        salesOrderRepo.findById(invoice.getOrderId()).ifPresent(order -> {
            if ("CANCELLED".equals(order.getStatus()) || "COMPLETED".equals(order.getStatus())) {
                return;
            }
            order.setStatus("COMPLETED");
            salesOrderRepo.save(order);
        });
    }

    /**
     * When a vendor payable for a PO is confirmed, mark the linked purchase invoice paid (or partially paid)
     * if one exists. No-op when there is no PURCHASE invoice for the PO (legacy data).
     */
    public void applyPurchaseInvoicePaymentForPurchaseOrder(Long purchaseOrderId, BigDecimal amount) {
        if (purchaseOrderId == null || amount == null) {
            return;
        }
        repo.findByOrderIdAndType(purchaseOrderId, InvoiceType.PURCHASE).ifPresent(inv -> {
            if (inv.getInvoiceId() == null || inv.getInvoiceId().isBlank() || inv.getCompany() == null) {
                return;
            }
            applyPayment(inv.getCompany().getId(), inv.getInvoiceId(), amount);
        });
    }

    /**
     * Ensures a receipt PDF exists for a fully paid generated purchase invoice.
     * The original invoice PDF ({@code pdfUrl}) is left unchanged.
     */
    public void regenerateGeneratedPurchaseInvoicePdfAfterVendorPayment(Long purchaseOrderId) {
        if (purchaseOrderId == null) {
            return;
        }
        repo.findByOrderIdAndType(purchaseOrderId, InvoiceType.PURCHASE)
                .filter(inv -> inv.getDocumentSource() == InvoiceDocumentSource.GENERATED)
                .filter(this::isFullyPaid)
                .ifPresent(inv -> generateAndUploadInvoicePdf(inv, true));
    }

    // ============================================================
    // READ OPERATIONS
    // ============================================================
    public InvoiceResponse getInvoiceById(Long id) {
        Invoice inv = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        assertInvoiceInTenant(inv);
        return toDTO(inv);
    }

    public InvoiceResponse getInvoiceByCode(String invoiceCode) {
        Long companyId = auth.getCurrentCompanyId();
        if (companyId == null) {
            throw new RuntimeException("Invoice not found");
        }
        Invoice inv = repo.findByCompany_IdAndInvoiceId(companyId, invoiceCode)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        assertInvoiceInTenant(inv);
        return toDTO(inv);
    }

    public List<InvoiceResponse> getAllInvoices() {
        Long companyId = auth.getCurrentCompanyId();
        if (companyId == null && !isSuperAdmin()) {
            return List.of();
        }
        if (companyId == null) {
            return List.of();
        }
        return repo.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(this::toDTO)
                .toList();
    }

    public List<InvoiceResponse> listInvoicesForCurrentCompany(InvoiceType type) {
        Long companyId = auth.getCurrentCompanyId();
        if (companyId == null && !isSuperAdmin()) {
            return List.of();
        }
        if (companyId == null) {
            return List.of();
        }
        if (type == null) {
            return repo.findByCompanyIdOrderByCreatedAtDesc(companyId).stream().map(this::toDTO).toList();
        }
        return repo.findByCompany_IdAndTypeOrderByCreatedAtDesc(companyId, type).stream()
                .filter(inv -> type != InvoiceType.PURCHASE || isPurchaseInvoiceVisibleInAccountsPayable(inv))
                .map(this::toDTO)
                .toList();
    }

    private boolean isPurchaseInvoiceVisibleInAccountsPayable(Invoice invoice) {
        if (invoice.getOrderId() == null) {
            return true;
        }
        return purchaseOrderRepo.findById(invoice.getOrderId())
                .map(po -> po.getStatus() == PurchaseOrderStatus.CONFIRMED
                        || po.getStatus() == PurchaseOrderStatus.PARTIALLY_RECEIVED
                        || po.getStatus() == PurchaseOrderStatus.RECEIVED)
                .orElse(false);
    }

    public List<InvoiceResponse> getInvoicesByCustomer(String toParty) {
        Long companyId = auth.getCurrentCompanyId();
        if (companyId == null && !isSuperAdmin()) {
            return List.of();
        }
        if (isSuperAdmin()) {
            return repo.findByToPartyOrderByCreatedAtDesc(toParty).stream().map(this::toDTO).toList();
        }
        return repo.findByCompany_IdAndToPartyOrderByCreatedAtDesc(companyId, toParty).stream().map(this::toDTO).toList();
    }

    public List<InvoiceResponse> getInvoicesByStatus(Long companyId, String status) {
        Long effectiveCompanyId = isSuperAdmin() ? companyId : auth.getCurrentCompanyId();
        if (effectiveCompanyId == null) {
            return List.of();
        }
        return repo.findByCompanyIdAndStatusOrderByCreatedAtDesc(effectiveCompanyId, status)
                .stream().map(this::toDTO).toList();
    }

    public void emailInvoice(Long invoiceId) {
        Invoice invoice = repo.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        assertInvoiceInTenant(invoice);
        if (invoice.getType() != InvoiceType.SALES || invoice.getOrderId() == null) {
            return;
        }
        Customer customer = salesOrderRepo.findById(invoice.getOrderId())
                .map(so -> so.getCustomer())
                .orElse(null);
        customerEmailService.sendInvoiceCreatedEmail(customer, invoice);
    }

    public void emailReceipt(Long invoiceId) {
        Invoice invoice = repo.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        assertInvoiceInTenant(invoice);
        if (!"PAID".equalsIgnoreCase(invoice.getStatus())) {
            throw new RuntimeException("Receipt can be sent only for paid invoices");
        }
        if (invoice.getType() != InvoiceType.SALES || invoice.getOrderId() == null) {
            return;
        }
        Customer customer = salesOrderRepo.findById(invoice.getOrderId())
                .map(so -> so.getCustomer())
                .orElse(null);
        customerEmailService.sendReceiptEmail(customer, invoice);
    }

    // ============================================================
    // UPDATE
    // ============================================================
    public InvoiceResponse updateInvoice(Long id, InvoiceRequest req) {

        Invoice inv = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        assertInvoiceInTenant(inv);

        inv.setToParty(req.getToParty());
        inv.setInvoiceDate(req.getInvoiceDate());
        inv.setDueDate(req.getDueDate());
        inv.setItemDescription(req.getItemDescription());
        inv.setNotesRemarks(req.getNotesRemarks());
        inv.setGracePeriod(req.getGracePeriod());
        inv.setInterestRate(req.getInterestRate());
        inv.setPartyClassification(req.getPartyClassification());

        if (req.getAmount() != null) {
            inv.setAmount(req.getAmount());
            inv.setOpenAmount(req.getAmount());
            inv.setOutstanding(req.getAmount());
        }
        if (req.getSubtotalAmount() != null) {
            inv.setSubtotalAmount(req.getSubtotalAmount());
        }
        if (req.getDiscountAmount() != null) {
            inv.setDiscountAmount(req.getDiscountAmount());
        }
        if (req.getTaxAmount() != null) {
            inv.setTaxAmount(req.getTaxAmount());
        }
        if (req.getSupplierInvoiceNumber() != null) {
            String s = req.getSupplierInvoiceNumber().trim();
            inv.setSupplierInvoiceNumber(s.isEmpty() ? null : s);
        }
        if (req.getDocumentSource() != null) {
            inv.setDocumentSource(req.getDocumentSource());
        }
        if (req.getExternalDocumentUrl() != null) {
            String u = req.getExternalDocumentUrl().trim();
            inv.setExternalDocumentUrl(u.isEmpty() ? null : u);
            applyExternalLinkPdfHint(inv);
        }

        return toDTO(repo.save(inv));
    }

    public InvoiceResponse archiveInvoice(Long id) {
        Invoice inv = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        assertInvoiceInTenant(inv);
        String status = inv.getStatus() == null ? "" : inv.getStatus().trim().toUpperCase();
        if (!"PAID".equals(status) && !"CANCELLED".equals(status)) {
            throw new RuntimeException("Only paid or cancelled invoices can be archived");
        }
        if (!inv.isArchived()) {
            inv.setArchived(true);
            inv = repo.save(inv);
        }
        return toDTO(inv);
    }

    // ============================================================
    // DELETE
    // ============================================================
    public void deleteInvoice(Long id) {
        Invoice inv = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        assertInvoiceInTenant(inv);
        if (!inv.isArchived()) {
            throw new RuntimeException("Only archived invoices can be permanently deleted");
        }
        repo.deleteById(id);
    }

    // ============================================================
    // PRIVATE: PDF GENERATION + UPLOAD (SINGLE SOURCE OF TRUTH)
    // ============================================================
    /**
     * @param receipt when true, stores under {@code receiptPdfUrl}; otherwise under {@code pdfUrl}
     */
    private String generateAndUploadInvoicePdf(Invoice invoice, boolean receipt) {

        InvoiceDocumentSource src = invoice.getDocumentSource();
        if (src != null && src != InvoiceDocumentSource.GENERATED) {
            throw new RuntimeException("Internal PDF generation applies only to GENERATED documents");
        }

        try {
            byte[] pdfBytes = pdfService.generateInvoicePdf(invoice);

            String fileName = receipt
                    ? invoice.getInvoiceId() + "-receipt.pdf"
                    : invoice.getInvoiceId() + ".pdf";
            String storageKey = receipt
                    ? invoice.getId() + "/receipt"
                    : invoice.getId() + "/invoice";

            MultipartFile pdfFile = new InMemoryMultipartFile(
                    pdfBytes,
                    fileName,
                    "application/pdf"
            );

            FileUploadResult uploadResult = fileStorageService.upload(
                    pdfFile,
                    FileCategory.INVOICE_PDF,
                    storageKey,
                    true,
                    invoice.getCompany().getId()
            );

            String pdfUrl = fileStorageService.getPublicUrl(
                    uploadResult.getBlobPath()
            );

            if (receipt) {
                invoice.setReceiptPdfUrl(pdfUrl);
            } else {
                invoice.setPdfUrl(pdfUrl);
            }
            repo.save(invoice);

            return pdfUrl;

        } catch (Exception e) {
            throw new RuntimeException("Invoice PDF generation/upload failed", e);
        }
    }

    // ============================================================
    // MAP DOMAIN -> DTO
    // ============================================================
    private InvoiceResponse toDTO(Invoice i) {

        SalesOrderResponseDTO salesOrder = null;
        PurchaseOrderResponseDTO purchaseOrder = null;

        if (i.getType() == InvoiceType.SALES && i.getOrderId() != null) {
            salesOrder = salesOrderService.get(i.getOrderId());
        }

        if (i.getType() == InvoiceType.PURCHASE && i.getOrderId() != null) {
            purchaseOrder = purchaseOrderService.get(i.getOrderId());
        }

        CompanyInvoiceSettings invoiceSettings = getOrCreateInvoiceSettings(i.getCompany());

        String orderNumber = null;
        if (purchaseOrder != null) {
            orderNumber = purchaseOrder.getOrderNumber();
        } else if (salesOrder != null) {
            orderNumber = salesOrder.getOrderNumber();
        }

        BigDecimal invoiceTotal = i.getAmount() != null ? i.getAmount() : BigDecimal.ZERO;
        BigDecimal outstandingAmount = i.getOutstanding() != null ? i.getOutstanding() : invoiceTotal;
        Long invoiceCompanyId = i.getCompany() != null ? i.getCompany().getId() : null;
        BigDecimal paidAmount = i.getInvoiceId() != null && invoiceCompanyId != null
                ? paymentRepo.sumConfirmedAmountByCompanyIdAndInvoiceId(invoiceCompanyId, i.getInvoiceId())
                : BigDecimal.ZERO;
        // Whatever reduced the balance that wasn't cash was a directly-applied credit note.
        BigDecimal creditAppliedAmount = invoiceTotal.subtract(outstandingAmount).subtract(paidAmount);
        if (creditAppliedAmount.compareTo(BigDecimal.ZERO) < 0) {
            creditAppliedAmount = BigDecimal.ZERO;
        }

        return InvoiceResponse.builder()
                .id(i.getId())
                .invoiceId(i.getInvoiceId())
                .companyId(i.getCompany().getId())
                .companyName(i.getCompany().getCompanyName())
                .companyStreet(i.getCompany().getStreet())
                .companyCity(i.getCompany().getCity())
                .companyState(i.getCompany().getState())
                .companyCountry(i.getCompany().getCountry())
                .companyPhone(i.getCompany().getPhoneNo())
                .companyEmail(i.getCompany().getCompanyEmail())
                .billingEmail(i.getCompany().getBillingEmail())
                .companyWebsiteUrl(i.getCompany().getWebsiteUrl())
                .currencyCode(
                        i.getCompany().getCurrency() != null
                                ? i.getCompany().getCurrency().getCurrencyCode()
                                : null)
                .currencySymbol(
                        i.getCompany().getCurrency() != null
                                ? i.getCompany().getCurrency().getCurrencySymbol()
                                : null)
                .toParty(i.getToParty())
                .status(i.getStatus())
                .archived(i.isArchived())
                .invoiceDate(i.getInvoiceDate())
                .dueDate(i.getDueDate())
                .paidDate(i.getPaidDate())
                .amount(i.getAmount())
                .subtotalAmount(i.getSubtotalAmount() == null ? BigDecimal.ZERO : i.getSubtotalAmount())
                .discountAmount(i.getDiscountAmount() == null ? BigDecimal.ZERO : i.getDiscountAmount())
                .taxAmount(i.getTaxAmount() == null ? BigDecimal.ZERO : i.getTaxAmount())
                .openAmount(i.getOpenAmount())
                .outstanding(i.getOutstanding())
                .paidAmount(paidAmount)
                .creditAppliedAmount(creditAppliedAmount)
                .itemDescription(i.getItemDescription())
                .notesRemarks(i.getNotesRemarks())
                .gracePeriod(i.getGracePeriod())
                .interestRate(i.getInterestRate())
                .partyClassification(i.getPartyClassification())
                .pdfUrl(i.getPdfUrl())
                .receiptPdfUrl(i.getReceiptPdfUrl())
                .supplierInvoiceNumber(i.getSupplierInvoiceNumber())
                .documentSource(i.getDocumentSource())
                .externalDocumentUrl(i.getExternalDocumentUrl())
                .vendorInvoiceDocumentUrl(i.getVendorInvoiceDocumentUrl())
                .vendorInvoiceMatchedAt(i.getVendorInvoiceMatchedAt())
                .createdAt(i.getCreatedAt())
                .orderId(i.getOrderId())
                .orderNumber(orderNumber)
                .supplierId(purchaseOrder != null ? purchaseOrder.getSupplierId() : null)
                .supplierName(purchaseOrder != null
                        ? purchaseOrder.getSupplierName()
                        : (i.getType() == InvoiceType.PURCHASE ? i.getToParty() : null))
                .type(i.getType())
                .salesOrder(salesOrder)
                .purchaseOrder(purchaseOrder)
                .debitAccountId(i.getDebitAccount().getId())
                .debitAccountName(i.getDebitAccount().getAccountName())
                .creditAccountId(i.getCreditAccount().getId())
                .creditAccountName(i.getCreditAccount().getAccountName())
                .bankAccountId(i.getBankAccount() != null ? i.getBankAccount().getId() : null)
                .bankAccountName(i.getBankAccount() != null ? i.getBankAccount().getBankName() : null)
                .bankAccountNumber(i.getBankAccount() != null ? i.getBankAccount().getAccountNumber() : null)
                .bankIfscCode(i.getBankAccount() != null ? i.getBankAccount().getIfscCode() : null)
                .bankBranchName(i.getBankAccount() != null ? i.getBankAccount().getBranchName() : null)
                .invoiceHeaderSubtitle(invoiceSettings.getInvoiceHeaderSubtitle())
                .invoiceNotesUnpaid(invoiceSettings.getInvoiceNotesUnpaid())
                .invoiceNotesPaid(invoiceSettings.getInvoiceNotesPaid())
                .invoiceTerms(invoiceSettings.getInvoiceTerms())
                .invoiceFooterCompanyLine(invoiceSettings.getInvoiceFooterCompanyLine())
                .invoiceFooterTaxLine(invoiceSettings.getInvoiceFooterTaxLine())
                .invoiceFooterSignatureNote(invoiceSettings.getInvoiceFooterSignatureNote())
                .invoiceFooterSupportEmail(i.getCompany().getCompanyEmail() != null
                        ? i.getCompany().getCompanyEmail()
                        : invoiceSettings.getInvoiceFooterSupportEmail())
                .invoiceFooterBillingEmail(i.getCompany().getBillingEmail() != null
                        ? i.getCompany().getBillingEmail()
                        : invoiceSettings.getInvoiceFooterBillingEmail())
                .invoiceQrEnabled(invoiceSettings.isInvoiceQrEnabled())
                .publicInvoiceUrl(buildPublicInvoiceUrl(i))
                .build();
    }

    private String buildPublicInvoiceUrl(Invoice invoice) {
        CompanyInvoiceSettings invoiceSettings = getOrCreateInvoiceSettings(invoice.getCompany());
        if (!invoiceSettings.isInvoiceQrEnabled()) {
            return null;
        }
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return null;
        }
        String normalized = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        return normalized + "/public/invoices/" + invoice.getInvoiceId();
    }

    private CompanyInvoiceSettings getOrCreateInvoiceSettings(Company company) {
        return invoiceSettingsRepo.findByCompanyId(company.getId())
                .orElseGet(() -> invoiceSettingsRepo.save(InvoiceSettingsDefaults.buildDefaults(company)));
    }

    private boolean isSuperAdmin() {
        String r = auth.getCurrentUserRole();
        return r != null && "SUPER_ADMIN".equalsIgnoreCase(r);
    }

    private void assertInvoiceInTenant(Invoice inv) {
        if (isSuperAdmin()) {
            return;
        }
        Long cid = auth.getCurrentCompanyId();
        if (cid == null || inv.getCompany() == null || !cid.equals(inv.getCompany().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invoice not found or access denied");
        }
    }
}