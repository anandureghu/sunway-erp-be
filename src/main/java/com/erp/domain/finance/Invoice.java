package com.erp.domain.finance;

import com.erp.domain.InvoiceType;
import com.erp.domain.hr.Company;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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

    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private LocalDate paidDate;

    private BigDecimal amount;
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

    @PrePersist
    public void onCreate() {
        if (createdAt == null)
            createdAt = Instant.now();
    }
}
