package com.erp.domain.finance;

import com.erp.domain.User;
import com.erp.domain.hr.Department;
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
        name = "budget_lines",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_budget_line",
                columnNames = {"budget_header_id", "account_id", "department_id", "period"}
        )
)
public class BudgetLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Parent budget reference
    @ManyToOne(optional = false)
    @JoinColumn(name = "budget_header_id")
    private BudgetHeader budgetHeader;

    // GL Account
    @ManyToOne(optional = false)
    @JoinColumn(name = "account_id")
    private ChartOfAccounts account;

    // Optional Department
    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    private String projectId;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(length = 250)
    private String notes;

    @Column(nullable = false, length = 20)
    private BudgetStatus status;
    // Draft, Active, Closed

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
