package com.erp.domain.finance;

import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.Department;
import jakarta.persistence.*;
import lombok.*;

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
    private Integer budgetYear;

    private Long amount;

    private LocalDate startDate;
    private LocalDate endDate;

    @Column(nullable = false, length = 20)
    private BudgetStatus status;
    // Draft, Active, Closed

    // Optional Department
    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    private String projectId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @ManyToOne
    @JoinColumn(name = "approved_by_user_id")
    private User approvedByUser;

    private Instant createdAt;
    private Instant updatedAt;

    @OneToMany(mappedBy = "budgetHeader", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BudgetLine> lines;

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
