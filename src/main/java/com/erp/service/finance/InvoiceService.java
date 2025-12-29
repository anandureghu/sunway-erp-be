package com.erp.service.finance;

import com.erp.domain.finance.Invoice;
import com.erp.domain.hr.Company;
import com.erp.dto.finance.InvoiceRequest;
import com.erp.dto.finance.InvoiceResponse;
import com.erp.repo.finance.InvoiceRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.service.pdf.InvoicePDFService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository repo;
    private final CompanyRepository companyRepo;
    private final InvoicePDFService pdfService;

    // ============================================================
    // CREATE MANUAL INVOICE
    // ============================================================
    public InvoiceResponse createInvoice(InvoiceRequest req) {

        Company company = companyRepo.findById(req.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found"));

        String invoiceCode = "INV-" + UUID.randomUUID().toString().substring(0, 8);

        Invoice invoice = Invoice.builder()
                .invoiceId(invoiceCode)
                .company(company)
                .toParty(req.getToParty())
                .invoiceDate(req.getInvoiceDate() == null ? Instant.now() : req.getInvoiceDate())
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
                .build();

        Invoice saved = repo.save(invoice);

        try {
            byte[] pdfBytes = pdfService.generateInvoicePdf(
                    invoiceCode,
                    company.getCompanyName(),
                    req.getItemDescription(),
                    req.getAmount().toString()
            );

//            String pdfUrl = storage.savePdf(invoiceCode, pdfBytes);
//            saved.setPdfUrl(pdfUrl);
            saved = repo.save(saved);

        } catch (Exception e) {
            throw new RuntimeException("PDF generation failed");
        }

        return toDTO(saved);
    }

    // ============================================================
    // AUTO GENERATE INVOICE FOR PAYMENT
    // ============================================================
    public Invoice autoGenerateInvoice(Company company, BigDecimal amount, String notes) {

        String invoiceCode = "INV-" + System.currentTimeMillis();

        Invoice invoice = Invoice.builder()
                .invoiceId(invoiceCode)
                .company(company)
                .status("UNPAID")
                .invoiceDate(Instant.now())
                .amount(amount)
                .openAmount(amount)
                .outstanding(amount)
                .itemDescription(notes)
                .createdAt(Instant.now())
                .build();

        Invoice saved = repo.save(invoice);

        try {
            byte[] pdf = pdfService.generateInvoicePdf(
                    invoiceCode, company.getCompanyName(), notes, amount.toString()
            );

//            String pdfUrl = storage.savePdf(invoiceCode, pdf);
//            saved.setPdfUrl(pdfUrl);
            saved = repo.save(saved);

        } catch (Exception ignored) {
        }

        return saved;
    }

    // ============================================================
    // APPLY PAYMENT TO EXISTING INVOICE
    // ============================================================
    public Invoice applyPayment(String invoiceId, BigDecimal amount) {
        Invoice inv = repo.findByInvoiceId(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        BigDecimal newOutstanding = inv.getOutstanding().subtract(amount);

        if (newOutstanding.compareTo(BigDecimal.ZERO) <= 0) {
            inv.setOutstanding(BigDecimal.ZERO);
            inv.setOpenAmount(BigDecimal.ZERO);
            inv.setStatus("PAID");
            inv.setPaidDate(Instant.now());
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
    public Invoice getByInvoiceId(String invoiceId) {
        return repo.findByInvoiceId(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
    }

    public InvoiceResponse getInvoiceById(Long id) {
        Invoice inv = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        return toDTO(inv);
    }

    public List<InvoiceResponse> getAllInvoices() {
        return repo.findAll().stream().map(this::toDTO).toList();
    }

    public List<InvoiceResponse> getInvoicesByCustomer(String toParty) {
        return repo.findByToParty(toParty)
                .stream().map(this::toDTO).toList();
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
    // MAP DOMAIN -> DTO
    // ============================================================
    private InvoiceResponse toDTO(Invoice i) {
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
                .build();
    }
}
