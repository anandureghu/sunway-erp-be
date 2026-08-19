package com.erp.service.finance;

import com.erp.domain.InvoiceType;
import com.erp.domain.finance.CreditNote;
import com.erp.domain.finance.Invoice;
import com.erp.domain.finance.Payment;
import com.erp.domain.finance.PaymentDirection;
import com.erp.domain.hr.Company;
import com.erp.domain.purchase.GoodsReceipt;
import com.erp.dto.finance.CreateCreditNoteDTO;
import com.erp.dto.finance.CreditNoteResponseDTO;
import com.erp.repo.finance.CreditNoteRepository;
import com.erp.repo.finance.InvoiceRepository;
import com.erp.repo.finance.PaymentRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.inventory.CustomerRepository;
import com.erp.repo.inventory.VendorRepository;
import com.erp.repo.purchase.GoodsReceiptRepository;
import com.erp.repo.purchase.PurchaseOrderRepository;
import com.erp.repo.sales.SalesOrderRepository;
import com.erp.security.context.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.service.DocumentSequenceService;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final VendorRepository vendorRepository;
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

        Invoice invoice = invoiceRepository.findByCompany_IdAndInvoiceId(companyId, dto.getInvoiceId())
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
            Long companyId, String invoiceId, BigDecimal amount, String reason, Long goodsReceiptId) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        if (companyId == null || invoiceId == null || invoiceId.isBlank()) {
            throw new RuntimeException("Invoice not found");
        }

        Invoice invoice = invoiceRepository.findByCompany_IdAndInvoiceId(companyId, invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

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

    /**
     * Standing customer credit for a paid sales invoice when goods are returned.
     * Available to apply on future payments or cash out anytime.
     */
    @Transactional
    public CreditNoteResponseDTO createAutomaticCreditNoteForCustomerReturn(
            Long companyId, String invoiceId, BigDecimal amount, String reason, Long customerId) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        if (companyId == null || invoiceId == null || invoiceId.isBlank()) {
            throw new RuntimeException("Invoice not found");
        }

        Invoice invoice = invoiceRepository.findByCompany_IdAndInvoiceId(companyId, invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company doesn't exist"));

        Long resolvedCustomerId = customerId != null ? customerId : resolveCustomerId(invoice);

        CreditNote creditNote = CreditNote.builder()
                .creditNoteNumber(documentSequenceService.generateNext("CN"))
                .invoice(invoice)
                .company(company)
                .amount(amount)
                .remainingAmount(amount)
                .status("AVAILABLE")
                .source("AUTO_CUSTOMER_RETURN")
                .customerId(resolvedCustomerId)
                .creditDate(java.time.LocalDate.now())
                .reason(reason)
                .createdAt(OffsetDateTime.now())
                .build();

        creditNoteRepository.save(creditNote);
        return mapToResponse(creditNote);
    }

    /**
     * Cashes out remaining standing credit as a recorded refund/redemption payment.
     * Customer notes → CUSTOMER direction outflow record; supplier notes → VENDOR receipt record.
     */
    @Transactional
    public CreditNoteResponseDTO cashOut(Long creditNoteId) {
        Long companyId = auth.getCurrentCompanyId();
        CreditNote note = creditNoteRepository.findById(creditNoteId)
                .orElseThrow(() -> new RuntimeException("Credit note not found"));

        if (note.getCompany() == null || !companyId.equals(note.getCompany().getId())) {
            throw new RuntimeException("Credit note not found or access denied");
        }
        BigDecimal remaining = note.getRemainingAmount();
        if (remaining == null || remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Credit note has no remaining balance to cash out");
        }
        String status = note.getStatus() == null ? "" : note.getStatus().toUpperCase();
        if (!"AVAILABLE".equals(status) && !"PARTIALLY_APPLIED".equals(status)) {
            throw new RuntimeException("Only available credit notes can be cashed out");
        }

        Invoice invoice = note.getInvoice();
        boolean supplierCredit = note.getSupplierId() != null
                || (invoice != null && invoice.getType() == InvoiceType.PURCHASE);

        PaymentDirection direction = supplierCredit ? PaymentDirection.VENDOR : PaymentDirection.CUSTOMER;
        String payee = supplierCredit
                ? resolveSupplierName(note)
                : resolveCustomerName(note);

        Payment cashOutPayment = Payment.builder()
                .paymentCode(documentSequenceService.generateNext("CNR"))
                .company(note.getCompany())
                .amount(remaining)
                .paymentMethod("CREDIT_NOTE_CASHOUT")
                .effectiveDate(LocalDate.now())
                .notes("Cash-out of credit note " + note.getCreditNoteNumber()
                        + (payee != null ? " (" + payee + ")" : "")
                        + (note.getReason() != null ? ": " + note.getReason() : ""))
                .invoiceId(invoice != null ? invoice.getInvoiceId() : null)
                .paymentDirection(direction)
                .purchaseOrderId(invoice != null && invoice.getType() == InvoiceType.PURCHASE
                        ? invoice.getOrderId() : null)
                .payee(payee)
                .expenseCategory(supplierCredit ? null : "CREDIT_NOTE_REFUND")
                .createdBy(auth.getCurrentUserId())
                .build();
        cashOutPayment.setPdfUrl("https://dummy.url/payments/" + cashOutPayment.getPaymentCode() + ".pdf");
        paymentRepository.save(cashOutPayment);

        note.setRemainingAmount(BigDecimal.ZERO);
        note.setStatus("CASHED");
        // Stash payment code in reason suffix only if we need it — expose via DTO from payment lookup
        creditNoteRepository.save(note);

        CreditNoteResponseDTO dto = mapToResponse(note);
        dto.setCashOutPaymentCode(cashOutPayment.getPaymentCode());
        return dto;
    }

    private CreditNoteResponseDTO mapToResponse(CreditNote creditNote) {

        Invoice invoice = creditNote.getInvoice();
        boolean isPurchase = invoice != null && invoice.getType() == InvoiceType.PURCHASE
                || creditNote.getSupplierId() != null;

        String customerName = !isPurchase
                ? firstNonBlank(
                        invoice != null ? invoice.getToParty() : null,
                        resolveCustomerName(creditNote))
                : null;
        String supplierName = isPurchase
                ? firstNonBlank(
                        invoice != null ? invoice.getToParty() : null,
                        resolveSupplierName(creditNote))
                : null;

        return CreditNoteResponseDTO.builder()
                .id(creditNote.getId())
                .creditNoteNumber(creditNote.getCreditNoteNumber())
                .creditNoteDate(creditNote.getCreditDate())
                .customerName(customerName)
                .supplierName(supplierName)
                .customerId(creditNote.getCustomerId())
                .supplierId(creditNote.getSupplierId())
                .status(creditNote.getStatus())
                .project(creditNote.getProject())
                .referenceNumber(invoice != null ? invoice.getInvoiceId() : null)
                .source(creditNote.getSource())
                .reason(creditNote.getReason())
                .amount(creditNote.getAmount())
                .remainingAmount(creditNote.getRemainingAmount())
                .build();
    }

    private String resolveCustomerName(CreditNote note) {
        if (note.getCustomerId() == null) {
            return null;
        }
        return customerRepository.findById(note.getCustomerId())
                .map(c -> c.getCustomerName())
                .orElse(null);
    }

    private String resolveSupplierName(CreditNote note) {
        if (note.getSupplierId() == null) {
            return null;
        }
        return vendorRepository.findById(note.getSupplierId())
                .map(v -> v.getVendorName())
                .orElse(null);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
