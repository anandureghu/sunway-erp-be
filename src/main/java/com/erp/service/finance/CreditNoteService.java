package com.erp.service.finance;

import com.erp.domain.finance.CreditNote;
import com.erp.domain.finance.Invoice;
import com.erp.domain.hr.Company;
import com.erp.domain.purchase.GoodsReceipt;
import com.erp.dto.finance.CreateCreditNoteDTO;
import com.erp.dto.finance.CreditNoteResponseDTO;
import com.erp.repo.finance.CreditNoteRepository;
import com.erp.repo.finance.InvoiceRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.purchase.GoodsReceiptRepository;
import com.erp.security.context.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.service.DocumentSequenceService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreditNoteService {

    private final CreditNoteRepository creditNoteRepository;
    private final CompanyRepository companyRepository;
    private final InvoiceRepository invoiceRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final AuthContext auth;
    private final DocumentSequenceService documentSequenceService;

    public List<CreditNoteResponseDTO> getAllForCompany() {
        Long companyId = auth.getCurrentCompanyId();

        return creditNoteRepository
                .findByCompanyIdOrderByCreatedAtDesc(companyId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public CreditNoteResponseDTO createCreditNote(CreateCreditNoteDTO dto) {

        Long companyId = auth.getCurrentCompanyId();

        Invoice invoice = invoiceRepository.findByInvoiceId(dto.getInvoiceId())
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        if (invoice.getCompany() == null
                || !companyId.equals(invoice.getCompany().getId())) {
            throw new RuntimeException("Invoice not found or access denied");
        }

        if (dto.getAmount().compareTo(invoice.getOutstanding()) > 0) {
            throw new RuntimeException("Credit exceeds outstanding amount");
        }

        Company company = companyRepository.findById(companyId).orElseThrow(() -> new RuntimeException("Company doesn't exist"));

        CreditNote creditNote = CreditNote.builder()
                .creditNoteNumber(documentSequenceService.generateNext("CN"))
                .invoice(invoice)
                .company(company)
                .amount(dto.getAmount())
                .remainingAmount(BigDecimal.ZERO) // fully applied immediately
                .status("APPLIED")
                .creditDate(dto.getCreditDate())
                .reason(dto.getReason())
                .createdAt(OffsetDateTime.now())
                .build();

        BigDecimal newOutstanding = invoice.getOutstanding().subtract(dto.getAmount());
        invoice.setOutstanding(newOutstanding);

        if (newOutstanding.compareTo(BigDecimal.ZERO) == 0) {
            invoice.setStatus("ADJUSTED");
        } else {
            invoice.setStatus("PARTIALLY_ADJUSTED");
        }

        creditNoteRepository.save(creditNote);
        invoiceRepository.save(invoice);

        return mapToResponse(creditNote);
    }

    /**
     * Automatically creates an unapplied supplier credit when inspection rejects goods on a
     * purchase order whose invoice is already fully settled. Kept deliberately separate from
     * {@link #createCreditNote} — that method requires {@code amount <= invoice.getOutstanding()}
     * and immediately zeroes it out, which doesn't fit an invoice whose outstanding is already 0.
     * Does not touch the invoice's own amount/status; the invoice stays settled/paid, and the
     * credit is tracked as a standalone record for later manual application against future
     * purchases from the same supplier.
     */
    @Transactional
    public CreditNoteResponseDTO createAutomaticCreditNoteForRejection(
            String invoiceId, BigDecimal amount, String reason, Long goodsReceiptId) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        Invoice invoice = invoiceRepository.findByInvoiceId(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        Long companyId = invoice.getCompany().getId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company doesn't exist"));

        GoodsReceipt goodsReceipt = goodsReceiptId != null
                ? goodsReceiptRepository.findById(goodsReceiptId).orElse(null)
                : null;

        CreditNote creditNote = CreditNote.builder()
                .creditNoteNumber(documentSequenceService.generateNext("CN"))
                .invoice(invoice)
                .company(company)
                .amount(amount)
                .remainingAmount(amount) // unapplied - available for future use
                .status("AVAILABLE")
                .source("AUTO_REJECTION")
                .goodsReceipt(goodsReceipt)
                .creditDate(java.time.LocalDate.now())
                .reason(reason)
                .createdAt(OffsetDateTime.now())
                .build();

        creditNoteRepository.save(creditNote);

        return mapToResponse(creditNote);
    }

    private CreditNoteResponseDTO mapToResponse(CreditNote creditNote) {

        Invoice invoice = creditNote.getInvoice();

        return CreditNoteResponseDTO.builder()
                .id(creditNote.getId())
                .creditNoteNumber(creditNote.getCreditNoteNumber())
                .creditNoteDate(creditNote.getCreditDate())
                .customerName(invoice.getToParty())
                .status(creditNote.getStatus())
                .project(creditNote.getProject())
                .referenceNumber(invoice.getInvoiceId())
                .amount(creditNote.getAmount())
                .remainingAmount(creditNote.getRemainingAmount())
                .build();
    }
}
