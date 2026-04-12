package com.erp.service.finance;

import com.erp.domain.InvoiceType;
import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.finance.Invoice;
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

//        if (invoice.getPdfUrl() != null && !invoice.getPdfUrl().isBlank()) {
//            return invoice.getPdfUrl();
//        }

        return generateAndUploadInvoicePdf(invoice);
    }

    // ============================================================
    // CREATE MANUAL INVOICE
    // ============================================================
    public InvoiceResponse createInvoice(InvoiceRequest req) {

        if (repo.findByOrderIdAndType(req.getOrderId(), req.getType()).isPresent()) {
            throw new RuntimeException("Invoice already exists for selected order");
        }

        Company company = companyRepo.findById(auth.getCurrentCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found"));

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

        String invoiceCode = "INV-" + UUID.randomUUID().toString().substring(0, 8);

        Invoice invoice = Invoice.builder()
                .invoiceId(invoiceCode)
                .company(company)
                .toParty(req.getToParty())
                .invoiceDate(req.getInvoiceDate() == null ? LocalDate.now() : req.getInvoiceDate())
                .dueDate(req.getDueDate())
                .status("UNPAID")
                .amount(req.getAmount())
                .subtotalAmount(req.getAmount())
                .discountAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .openAmount(req.getAmount())
                .outstanding(req.getAmount())
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
                .build();

        Invoice saved = repo.save(invoice);

        if (req.getType() == InvoiceType.SALES) {
            createPendingPaymentEntry(saved);
        }

        // Generate & upload PDF once
        generateAndUploadInvoicePdf(saved);

        if (req.getType() == InvoiceType.SALES && req.getOrderId() != null) {
            Customer customer = salesOrderRepo.findById(req.getOrderId())
                    .map(so -> so.getCustomer())
                    .orElse(null);
            customerEmailService.sendInvoiceCreatedEmail(customer, saved);
        }

        return toDTO(saved);
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
        return repo.findAll().stream().map(this::toDTO).toList();
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

        if (i.getType() == InvoiceType.SALES) {
            salesOrder = salesOrderService.get(i.getOrderId());
        }

        if (i.getType() == InvoiceType.PURCHASE) {
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
