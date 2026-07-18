package com.erp.service.finance;

import com.erp.domain.InvoiceType;
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
import com.erp.repo.purchase.PurchaseOrderRepository;
import com.erp.repo.sales.SalesOrderRepository;
import com.erp.security.context.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.service.DocumentSequenceService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CreditNoteService {

    private final CreditNoteRepository creditNoteRepository;
    private final CompanyRepository companyRepository;
    private final InvoiceRepository invoiceRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
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

    public List<CreditNoteResponseDTO> getAvailableForCustomer(Long customerId) {
        if (customerId == null) {
            return List.of();
        }
        return creditNoteRepository
                .findAvailableForCustomer(auth.getCurrentCompanyId(), customerId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<CreditNoteResponseDTO> getAvailableForSupplier(Long supplierId) {
        if (supplierId == null) {
            return List.of();
        }
        return creditNoteRepository
                .findAvailableForSupplier(auth.getCurrentCompanyId(), supplierId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /** Total unapplied credit note balance for a customer or supplier (whichever id is non-null). */
    public BigDecimal getAvailableCreditTotal(Long customerId, Long supplierId) {
        List<CreditNote> notes = availableNotesFor(customerId, supplierId);
        return notes.stream()
                .map(CreditNote::getRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<CreditNote> availableNotesFor(Long customerId, Long supplierId) {
        Long companyId = auth.getCurrentCompanyId();
        if (customerId != null) {
            return creditNoteRepository.findAvailableForCustomer(companyId, customerId);
        }
        if (supplierId != null) {
            return creditNoteRepository.findAvailableForSupplier(companyId, supplierId);
        }
        return List.of();
    }

    /**
     * Consumes available credit notes for a customer/supplier (oldest first) up to
     * {@code requestedAmount}, capped by however much is actually available. Only mutates the
     * credit notes themselves (remainingAmount/status) — callers are responsible for reducing the
     * relevant invoice's outstanding balance by the amount returned.
     */
    @Transactional
    public BigDecimal applyAvailableCredit(Long customerId, Long supplierId, BigDecimal requestedAmount) {
        if (requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        List<CreditNote> available = availableNotesFor(customerId, supplierId);

        BigDecimal remainingToApply = requestedAmount;
        BigDecimal totalApplied = BigDecimal.ZERO;
        for (CreditNote note : available) {
            if (remainingToApply.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal consume = note.getRemainingAmount().min(remainingToApply);
            if (consume.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal newRemaining = note.getRemainingAmount().subtract(consume);
            note.setRemainingAmount(newRemaining);
            note.setStatus(newRemaining.compareTo(BigDecimal.ZERO) == 0 ? "APPLIED" : "PARTIALLY_APPLIED");
            creditNoteRepository.save(note);
            remainingToApply = remainingToApply.subtract(consume);
            totalApplied = totalApplied.add(consume);
        }
        return totalApplied;
    }

    @Transactional
    public CreditNoteResponseDTO createCreditNote(CreateCreditNoteDTO dto) {

        Long companyId = auth.getCurrentCompanyId();

        Invoice invoice = invoiceRepository.findFirstByInvoiceIdOrderByCreatedAtDesc(dto.getInvoiceId())
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        if (invoice.getCompany() == null
                || !companyId.equals(invoice.getCompany().getId())) {
            throw new RuntimeException("Invoice not found or access denied");
        }

        Company company = companyRepository.findById(companyId).orElseThrow(() -> new RuntimeException("Company doesn't exist"));

        boolean applyImmediately = dto.getApplyImmediately() == null || dto.getApplyImmediately();

        Long customerId = resolveCustomerId(invoice);
        Long supplierId = resolveSupplierId(invoice);

        CreditNote.CreditNoteBuilder builder = CreditNote.builder()
                .creditNoteNumber(documentSequenceService.generateNext("CN"))
                .invoice(invoice)
                .company(company)
                .amount(dto.getAmount())
                .creditDate(dto.getCreditDate())
                .reason(dto.getReason())
                .createdAt(OffsetDateTime.now())
                .customerId(customerId)
                .supplierId(supplierId);

        if (applyImmediately) {
            if (dto.getAmount().compareTo(invoice.getOutstanding()) > 0) {
                throw new RuntimeException("Credit exceeds outstanding amount");
            }
            BigDecimal newOutstanding = invoice.getOutstanding().subtract(dto.getAmount());
            invoice.setOutstanding(newOutstanding);
            invoice.setStatus(newOutstanding.compareTo(BigDecimal.ZERO) == 0 ? "ADJUSTED" : "PARTIALLY_ADJUSTED");
            invoiceRepository.save(invoice);
            builder.remainingAmount(BigDecimal.ZERO).status("APPLIED"); // fully applied immediately
        } else {
            builder.remainingAmount(dto.getAmount()).status("AVAILABLE"); // standing credit, not yet applied
        }

        CreditNote creditNote = builder.build();
        creditNoteRepository.save(creditNote);

        return mapToResponse(creditNote);
    }

    private Long resolveCustomerId(Invoice invoice) {
        if (invoice.getType() != InvoiceType.SALES || invoice.getOrderId() == null) {
            return null;
        }
        return salesOrderRepository.findById(invoice.getOrderId())
                .map(so -> so.getCustomer() != null ? so.getCustomer().getId() : null)
                .orElse(null);
    }

    private Long resolveSupplierId(Invoice invoice) {
        if (invoice.getType() != InvoiceType.PURCHASE || invoice.getOrderId() == null) {
            return null;
        }
        return purchaseOrderRepository.findById(invoice.getOrderId())
                .map(po -> po.getSupplier() != null ? po.getSupplier().getId() : null)
                .orElse(null);
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

        Invoice invoice = invoiceRepository.findFirstByInvoiceIdOrderByCreatedAtDesc(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        Long companyId = invoice.getCompany().getId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company doesn't exist"));

        GoodsReceipt goodsReceipt = goodsReceiptId != null
                ? goodsReceiptRepository.findById(goodsReceiptId).orElse(null)
                : null;

        Long supplierId = goodsReceipt != null && goodsReceipt.getPurchaseOrder() != null
                && goodsReceipt.getPurchaseOrder().getSupplier() != null
                ? goodsReceipt.getPurchaseOrder().getSupplier().getId()
                : resolveSupplierId(invoice);

        CreditNote creditNote = CreditNote.builder()
                .creditNoteNumber(documentSequenceService.generateNext("CN"))
                .invoice(invoice)
                .company(company)
                .amount(amount)
                .remainingAmount(amount) // unapplied - available for future use
                .status("AVAILABLE")
                .source("AUTO_REJECTION")
                .goodsReceipt(goodsReceipt)
                .supplierId(supplierId)
                .creditDate(java.time.LocalDate.now())
                .reason(reason)
                .createdAt(OffsetDateTime.now())
                .build();

        creditNoteRepository.save(creditNote);

        return mapToResponse(creditNote);
    }

    private CreditNoteResponseDTO mapToResponse(CreditNote creditNote) {

        Invoice invoice = creditNote.getInvoice();
        boolean isPurchase = invoice != null && invoice.getType() == InvoiceType.PURCHASE;

        return CreditNoteResponseDTO.builder()
                .id(creditNote.getId())
                .creditNoteNumber(creditNote.getCreditNoteNumber())
                .creditNoteDate(creditNote.getCreditDate())
                .customerName(!isPurchase && invoice != null ? invoice.getToParty() : null)
                .supplierName(isPurchase && invoice != null ? invoice.getToParty() : null)
                .customerId(creditNote.getCustomerId())
                .supplierId(creditNote.getSupplierId())
                .status(creditNote.getStatus())
                .project(creditNote.getProject())
                .referenceNumber(invoice != null ? invoice.getInvoiceId() : null)
                .amount(creditNote.getAmount())
                .remainingAmount(creditNote.getRemainingAmount())
                .build();
    }
}
