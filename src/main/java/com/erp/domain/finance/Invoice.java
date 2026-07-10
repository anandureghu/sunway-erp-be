package com.erp.domain.finance;

import com.erp.domain.InvoiceType;
import com.erp.domain.hr.BankAccount;
import com.erp.domain.hr.Company;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_id", unique = true)
    private String invoiceId;

    @ManyToOne
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    private InvoiceType type;
    private Long orderId;

    private String toParty;
    private String status;

    @Column(nullable = false)
    @Builder.Default
    private boolean archived = false;

    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private LocalDate paidDate;

    private BigDecimal amount;
    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private BigDecimal openAmount;
    private BigDecimal outstanding;

    @Column(length = 500)
    private String itemDescription;

    @Column(columnDefinition = "TEXT")
    private String notesRemarks;

    private Integer gracePeriod;
    private BigDecimal interestRate;

    private String partyClassification;

    @Column(name = "pdf_url")
    private String pdfUrl;

    /**
     * Generated receipt PDF after the invoice is fully paid.
     * Kept separate from {@link #pdfUrl} so the original unpaid invoice document is preserved.
     */
    @Column(name = "receipt_pdf_url", length = 1024)
    private String receiptPdfUrl;

    /**
     * Vendor's own invoice number (for duplicate checks and display). Optional.
     */
    @Column(name = "supplier_invoice_number", length = 120)
    private String supplierInvoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_source", length = 32)
    @Builder.Default
    private InvoiceDocumentSource documentSource = InvoiceDocumentSource.GENERATED;

    /**
     * Supplier portal or external document URL when not stored as PDF in blob storage.
     */
    @Column(name = "external_document_url", length = 2000)
    private String externalDocumentUrl;

    /**
     * Vendor's own invoice document attached via "Match Vendor Invoice", kept separate from
     * {@link #pdfUrl} so matching never overwrites the system-generated invoice PDF.
     */
    @Column(name = "vendor_invoice_document_url", length = 2000)
    private String vendorInvoiceDocumentUrl;

    @Column(name = "vendor_invoice_matched_at")
    private Instant vendorInvoiceMatchedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    // GL Account
    @ManyToOne(optional = false)
    @JoinColumn(name = "credit_account")
    private ChartOfAccounts creditAccount;

    // GL Account
    @ManyToOne(optional = false)
    @JoinColumn(name = "debit_account")
    private ChartOfAccounts debitAccount;

    @ManyToOne
    @JoinColumn(name = "bank_account_id")
    private BankAccount bankAccount;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (documentSource == null) {
            documentSource = InvoiceDocumentSource.GENERATED;
        }
    }

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL)
    private List<CreditNote> creditNotes = new ArrayList<>();

}
