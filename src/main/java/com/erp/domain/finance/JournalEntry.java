package com.erp.domain.finance;

import com.erp.domain.User;
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
@Table(name = "journal_entries")
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "journal_entry_number", nullable = false, length = 50, unique = true)
    private String journalEntryNumber;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "period_id")
    private Long periodId;

    @Column(length = 30)
    private String source; // Manual, AP, AR, System

    @Column(length = 20)
    private String status; // Draft, Pending, Posted, Reversed

    @Column(length = 500)
    private String description;

    @Column(name = "total_debit_amount", precision = 18, scale = 2)
    private BigDecimal totalDebitAmount = BigDecimal.ZERO;

    @Column(name = "total_credit_amount", precision = 18, scale = 2)
    private BigDecimal totalCreditAmount = BigDecimal.ZERO;

    // Audit users
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedByUser;

    // Multi-company enforcement
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    // Relations
    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JournalLine> lines = new ArrayList<>();

    // NEW: Posting timestamps
    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    // NEW: Link to reversal entry
    @Column(name = "reversal_entry_id")
    private Long reversalEntryId;

    // Audit timestamps
    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
