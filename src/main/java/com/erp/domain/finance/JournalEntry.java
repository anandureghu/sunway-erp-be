package com.erp.domain.finance;

import com.erp.domain.User;
import com.erp.domain.hr.Company;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "journal_entries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "je_number", unique = true)
    private String jeNumber;

    @ManyToOne()
    @JoinColumn(name = "credit_account_id", nullable = false)
    private ChartOfAccounts creditAccount;

    @ManyToOne()
    @JoinColumn(name = "debit_account_id", nullable = false)
    private ChartOfAccounts debitAccount;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    private String source;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JournalEntryStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = (this.status == null) ? JournalEntryStatus.PENDING_APPROVAL : this.status;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
