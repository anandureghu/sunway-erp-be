package com.erp.domain.finance;

import com.erp.domain.User;
import com.erp.domain.hr.Company;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "budget_headers")
public class BudgetHeader {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String budgetName;

    @Column(nullable = false)
    private String fiscalYear;

    private BigDecimal amount;

    private LocalDate startDate;
    private LocalDate endDate;

    @Column(nullable = false, length = 20)
    private BudgetStatus status;

    @ManyToOne()
    @JoinColumn(name = "parent_budget")
    private BudgetHeader parentBudget;

    private Long reviseCount = 0L;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @ManyToOne
    @JoinColumn(name = "updated_by_user_id")
    private User updatedByUser;

    @ManyToOne
    @JoinColumn(name = "approved_by_user_id")
    private User approvedByUser;

    private Instant createdAt;
    private Instant updatedAt;

    @OneToMany(mappedBy = "budgetHeader", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BudgetLine> lines;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
        if (isActive == null) {
            isActive = Boolean.TRUE;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    @PostLoad
    void normalizeIsActive() {
        if (isActive == null) {
            isActive = Boolean.TRUE;
        }
    }
}
