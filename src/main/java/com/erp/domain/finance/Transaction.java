package com.erp.domain.finance;

import com.erp.domain.hr.Company;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // business / human readable id (optional)
    @Column(name = "transaction_code", length = 64, unique = true)
    private String transactionCode;

    @Column(name = "transaction_type", length = 50)
    private String transactionType; // e.g., PAYMENT, JOURNAL, RECEIPT, TRANSFER

    @ManyToOne
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "transaction_date")
    private LocalDate transactionDate;

//    @Column(name = "posted_date")
//    private Instant postedDate;

    @Column(name = "amount", precision = 18, scale = 2)
    private BigDecimal amount;

//    @Column(name = "debit_account", length = 64)
//    private String debitAccount;

    @Column(name = "credit_account", length = 64)
    private String creditAccount;
//
//    @Column(name = "item_code", length = 64)
//    private String itemCode;

    @Column(name = "invoice_id", length = 64)
    private String invoiceId;

    @Column(name = "payment_id", length = 64)
    private String paymentId;

//    @Column(name = "is_posted")
//    private Boolean posted = Boolean.FALSE;

    @Column(name = "transaction_description", length = 500)
    private String transactionDescription;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @PrePersist
    public void prePersist() { if (createdAt == null) createdAt = Instant.now(); }
}
