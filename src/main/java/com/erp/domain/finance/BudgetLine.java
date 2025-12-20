package com.erp.domain.finance;

import com.erp.domain.hr.Department;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

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

    private Long projectId;

    // Usually 1–12 for months
    @Column(nullable = false)
    private Integer period;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(length = 250)
    private String notes;
}
