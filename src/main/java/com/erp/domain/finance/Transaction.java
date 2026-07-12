package com.erp.domain.finance;

import com.erp.domain.User;
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
@Table(
        name = "transactions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_transactions_company_transaction_code", columnNames = {"company_id", "transaction_code"})
        }
)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // business / human readable id (optional)
    @Column(name = "transaction_code", length = 64)
    private String transactionCode;

    @Column(name = "transaction_type", length = 50)
    private String transactionType; // e.g., PAYMENT, JOURNAL, RECEIPT, TRANSFER

    @ManyToOne
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "transaction_date")
    private LocalDate transactionDate;

    @Column(name = "amount", precision = 18, scale = 2)
    private BigDecimal amount;

    /** Nullable when transaction is debit-only (single-sided). */
    @ManyToOne
    @JoinColumn(name = "credit_account", nullable = true)
    private ChartOfAccounts creditAccount;

    /** Nullable when transaction is credit-only (single-sided). */
    @ManyToOne
    @JoinColumn(name = "debit_account", nullable = true)
    private ChartOfAccounts debitAccount;

    /** e.g. UNKNOWN, CASH, or user-defined label after one-time edit. */
    @Column(name = "source", length = 64)
    private String source;

    /** After source is set to a value other than UNKNOWN, edits are blocked. */
    @Column(name = "source_locked", nullable = false)
    @Builder.Default
    private Boolean sourceLocked = false;

//    @Column(name = "item_code", length = 64)
//    private String itemCode;

    @Column(name = "invoice_id", length = 64)
    private String invoiceId;

    @Column(name = "payment_id", length = 64)
    private String paymentId;

    @Column(name = "transaction_description", length = 500)
    private String transactionDescription;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "related_id")
    private Long relatedId;

    @Column(name = "related_sub_id")
    private Long relatedSubId;

    @Builder.Default
    @Column(name = "archived", nullable = false)
    private Boolean archived = false;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @ManyToOne
    @JoinColumn(name = "archived_by")
    private User archivedByUser;

}
