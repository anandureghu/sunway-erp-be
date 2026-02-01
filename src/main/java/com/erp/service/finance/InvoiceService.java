package com.erp.service.finance;

import com.erp.domain.InvoiceType;
import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.finance.Invoice;
import com.erp.domain.hr.Company;
import com.erp.dto.file.FileCategory;
import com.erp.dto.file.FileUploadResult;
import com.erp.dto.finance.CreatePaymentDTO;
import com.erp.dto.finance.InvoiceRequest;
import com.erp.dto.finance.InvoiceResponse;
import com.erp.dto.purchase.PurchaseOrderResponseDTO;
import com.erp.dto.sales.SalesOrderResponseDTO;
import com.erp.repo.finance.ChartOfAccountsRepository;
import com.erp.repo.finance.InvoiceRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.file.FileStorageService;
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
    private final InvoicePDFService pdfService;
    private final FileStorageService fileStorageService;
    private final AuthContext auth;
    private final SalesOrderService salesOrderService;
    private final PurchaseOrderService purchaseOrderService;
    private final PaymentService paymentService;

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

        if (repo.findByOrderId(req.getOrderId()) != null) {
            throw new RuntimeException("Invoice already exists for selected order");
        }

        Company company = companyRepo.findById(auth.getCurrentCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found"));

        ChartOfAccounts debitAccount = coaRepo.findById(req.getDebitAccount())
                .orElseThrow(() -> new RuntimeException("Debit Account not found"));

        ChartOfAccounts creditAccount = coaRepo.findById(req.getCreditAccount())
                .orElseThrow(() -> new RuntimeException("Credit Account not found"));

        String invoiceCode = "INV-" + UUID.randomUUID().toString().substring(0, 8);

        Invoice invoice = Invoice.builder()
                .invoiceId(invoiceCode)
                .company(company)
                .toParty(req.getToParty())
                .invoiceDate(req.getInvoiceDate() == null ? LocalDate.now() : req.getInvoiceDate())
                .dueDate(req.getDueDate())
                .status("UNPAID")
                .amount(req.getAmount())
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
                .build();

        Invoice saved = repo.save(invoice);

        // Create payment record
        CreatePaymentDTO payment = CreatePaymentDTO.builder()
                .invoiceId(saved.getInvoiceId())
                .amount(saved.getAmount())
                .companyId(company.getId())
                .effectiveDate(saved.getDueDate())
                .build();

        paymentService.createPayment(payment);

        // Generate & upload PDF once
        generateAndUploadInvoicePdf(saved);

        return toDTO(saved);
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
                .build();
    }
}
