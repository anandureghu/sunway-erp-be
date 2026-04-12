package com.erp.service.finance;

import com.erp.domain.InvoiceType;
import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.finance.Invoice;
import com.erp.domain.finance.InvoiceDocumentSource;
import com.erp.domain.finance.Payment;
import com.erp.domain.hr.BankAccount;
import com.erp.domain.hr.Company;
import com.erp.domain.inventory.Customer;
import com.erp.dto.file.FileCategory;
import com.erp.dto.file.FileUploadResult;
import com.erp.dto.finance.InvoiceRequest;
import com.erp.dto.finance.InvoiceResponse;
import com.erp.dto.purchase.PurchaseOrderResponseDTO;
import com.erp.dto.sales.SalesOrderResponseDTO;
import com.erp.repo.finance.ChartOfAccountsRepository;
import com.erp.repo.finance.InvoiceRepository;
import com.erp.repo.finance.PaymentRepository;
import com.erp.repo.hr.BankAccountRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.sales.SalesOrderRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.file.FileStorageService;
import com.erp.service.notification.CustomerEmailService;
import com.erp.service.pdf.InvoicePDFService;
import com.erp.service.purchase.PurchaseOrderService;
import com.erp.service.sales.SalesOrderService;
import com.erp.util.InMemoryMultipartFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository repo;
    private final CompanyRepository companyRepo;
    private final ChartOfAccountsRepository coaRepo;
    private final BankAccountRepository bankAccountRepo;
    private final SalesOrderRepository salesOrderRepo;
    private final PaymentRepository paymentRepo;
    private final InvoicePDFService pdfService;
    private final FileStorageService fileStorageService;
    private final AuthContext auth;
    private final SalesOrderService salesOrderService;
    private final PurchaseOrderService purchaseOrderService;
    private final CustomerEmailService customerEmailService;

    // ============================================================
    // GET OR CREATE PDF (IDEMPOTENT)
    // ============================================================
    public String getOrCreateInvoicePdfUrl(Long invoiceId) {

        Invoice invoice = repo.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

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

        return generateAndUploadInvoicePdf(invoice);
    }

    // ============================================================
    // CREATE MANUAL INVOICE
    // ============================================================
    public InvoiceResponse createInvoice(InvoiceRequest req) {
        return createInvoice(req, null);
    }

    public InvoiceResponse createInvoice(InvoiceRequest req, MultipartFile supplierDocument) {

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
        if (bankId == null && company.getDefaultBankAccountId() != null) {
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

        String invoiceCode = "INV-" + UUID.randomUUID().toString().substring(0, 8);

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
        if (req.getType() == InvoiceType.SALES && req.getOrderId() != null) {
            if (repo.findByOrderIdAndType(req.getOrderId(), InvoiceType.SALES).isPresent()) {
                throw new RuntimeException("Invoice already exists for selected order");
            }
            return;
        }
        if (req.getType() == InvoiceType.PURCHASE
                && req.getOrderId() != null
                && req.getSupplierInvoiceNumber() != null
                && !req.getSupplierInvoiceNumber().isBlank()) {
            if (repo.findByCompany_IdAndOrderIdAndTypeAndSupplierInvoiceNumber(
                    company.getId(),
                    req.getOrderId(),
                    InvoiceType.PURCHASE,
                    req.getSupplierInvoiceNumber().trim()
            ).isPresent()) {
                throw new RuntimeException(
                        "A purchase invoice with this supplier invoice number already exists for this order");
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
        generateAndUploadInvoicePdf(refreshed);
    }

    public InvoiceResponse attachSupplierDocument(Long invoiceId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File is required");
        }
        Invoice inv = repo.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
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
                    true
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
        if (paymentRepo.findByInvoiceId(invoice.getInvoiceId()).isEmpty()) {
            Payment payment = Payment.builder()
                    .paymentCode("PAY-REQ-" + UUID.randomUUID().toString().substring(0, 8))
                    .company(invoice.getCompany())
                    .paymentMethod("PENDING_REQUEST")
                    .amount(invoice.getAmount())
                    .effectiveDate(invoice.getDueDate())
                    .notes("Auto-created payment request entry for invoice " + invoice.getInvoiceId())
                    .invoiceId(invoice.getInvoiceId())
                    .createdBy(auth.getCurrentUserId())
                    .build();
            paymentRepo.save(payment);
        }
    }

    public InvoiceResponse createInvoiceForConfirmedSalesOrder(Long salesOrderId) {
        var order = salesOrderRepo.findById(salesOrderId)
                .orElseThrow(() -> new RuntimeException("Sales order not found"));
        if (repo.findByOrderIdAndType(order.getId(), InvoiceType.SALES).isPresent()) {
            throw new RuntimeException("Invoice already exists for this sales order");
        }
        if (order.getDebitAccount() == null) {
            throw new RuntimeException("Sales order is missing debit account");
        }
        if (order.getCreditAccount() == null) {
            throw new RuntimeException("Sales order is missing credit account");
        }
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
        req.setDebitAccount(order.getDebitAccount().getId());
        req.setCreditAccount(order.getCreditAccount().getId());
        req.setBankAccountId(order.getBankAccount().getId());
        req.setItemDescription("Auto-generated from sales order " + order.getOrderNumber());
        req.setNotesRemarks("Invoice created on sales order confirmation.");
        InvoiceResponse response = createInvoice(req);
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

    // ============================================================
    // APPLY PAYMENT
    // ============================================================
    public Invoice applyPayment(String invoiceId, BigDecimal amount) {

        Invoice inv = repo.findByInvoiceId(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        BigDecimal newOutstanding = inv.getOutstanding().subtract(amount);

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

        return repo.save(inv);
    }

    // ============================================================
    // READ OPERATIONS
    // ============================================================
    public InvoiceResponse getInvoiceById(Long id) {
        return toDTO(repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found")));
    }

    public InvoiceResponse getInvoiceByCode(String invoiceCode) {
        return toDTO(repo.findByInvoiceId(invoiceCode)
                .orElseThrow(() -> new RuntimeException("Invoice not found")));
    }

    public List<InvoiceResponse> getAllInvoices() {
        return repo.findByCompanyId(auth.getCurrentCompanyId()).stream()
                .map(this::toDTO)
                .toList();
    }

    public List<InvoiceResponse> listInvoicesForCurrentCompany(InvoiceType type) {
        Long companyId = auth.getCurrentCompanyId();
        if (type == null) {
            return repo.findByCompanyId(companyId).stream().map(this::toDTO).toList();
        }
        return repo.findByCompany_IdAndType(companyId, type).stream().map(this::toDTO).toList();
    }

    public List<InvoiceResponse> getInvoicesByCustomer(String toParty) {
        return repo.findByToParty(toParty).stream().map(this::toDTO).toList();
    }

    public List<InvoiceResponse> getInvoicesByStatus(Long companyId, String status) {
        return repo.findByCompanyIdAndStatus(companyId, status)
                .stream().map(this::toDTO).toList();
    }

    public void emailInvoice(Long invoiceId) {
        Invoice invoice = repo.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
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

    // ============================================================
    // DELETE
    // ============================================================
    public void deleteInvoice(Long id) {
        repo.deleteById(id);
    }

    // ============================================================
    // PRIVATE: PDF GENERATION + UPLOAD (SINGLE SOURCE OF TRUTH)
    // ============================================================
    private String generateAndUploadInvoicePdf(Invoice invoice) {

        InvoiceDocumentSource src = invoice.getDocumentSource();
        if (src != null && src != InvoiceDocumentSource.GENERATED) {
            throw new RuntimeException("Internal PDF generation applies only to GENERATED documents");
        }

        try {
            byte[] pdfBytes = pdfService.generateInvoicePdf(invoice);

            MultipartFile pdfFile = new InMemoryMultipartFile(
                    pdfBytes,
                    invoice.getInvoiceId() + ".pdf",
                    "application/pdf"
            );

            FileUploadResult uploadResult = fileStorageService.upload(
                    pdfFile,
                    FileCategory.INVOICE_PDF,
                    invoice.getId().toString(),
                    true
            );

            String pdfUrl = fileStorageService.getPublicUrl(
                    uploadResult.getBlobPath()
            );

            invoice.setPdfUrl(pdfUrl);
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

        return InvoiceResponse.builder()
                .id(i.getId())
                .invoiceId(i.getInvoiceId())
                .companyId(i.getCompany().getId())
                .companyName(i.getCompany().getCompanyName())
                .toParty(i.getToParty())
                .status(i.getStatus())
                .invoiceDate(i.getInvoiceDate())
                .dueDate(i.getDueDate())
                .paidDate(i.getPaidDate())
                .amount(i.getAmount())
                .subtotalAmount(i.getSubtotalAmount() == null ? BigDecimal.ZERO : i.getSubtotalAmount())
                .discountAmount(i.getDiscountAmount() == null ? BigDecimal.ZERO : i.getDiscountAmount())
                .taxAmount(i.getTaxAmount() == null ? BigDecimal.ZERO : i.getTaxAmount())
                .openAmount(i.getOpenAmount())
                .outstanding(i.getOutstanding())
                .itemDescription(i.getItemDescription())
                .notesRemarks(i.getNotesRemarks())
                .gracePeriod(i.getGracePeriod())
                .interestRate(i.getInterestRate())
                .partyClassification(i.getPartyClassification())
                .pdfUrl(i.getPdfUrl())
                .supplierInvoiceNumber(i.getSupplierInvoiceNumber())
                .documentSource(i.getDocumentSource())
                .externalDocumentUrl(i.getExternalDocumentUrl())
                .createdAt(i.getCreatedAt())
                .orderId(i.getOrderId())
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
                .build();
    }
}
