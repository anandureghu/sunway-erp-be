package com.erp.domain.finance;

import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.Department;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Calendar;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "chart_of_accounts",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"company_id", "account_code"}),
        })
public class ChartOfAccounts {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_no", nullable = false, length = 7)
    private String accountNo;

    @Column(name = "inter_company_number", nullable = false, length = 3)
    private String interCompanyNumber;

    // system generated code
    // companyCode(3).department/projectCode(4).accountCode(6).intercompanyNumber(3)
    @Column(name = "account_code", nullable = false, length = 20)
    private String accountCode;

    @Column(name = "account_name", nullable = false, length = 255)
    private String accountName;

    private String description;

    // asset, liability, income, expense, equity
    private COAType type;

    @ManyToOne
    @JoinColumn(name = "parent_id")
    private ChartOfAccounts parent;

    private BigDecimal balance;

    @Column(name = "as_of_date")
    private Instant asOfDate;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @ManyToOne
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "project_code", length = 6)
    private String projectCode;

    @Column(name = "is_active")
    private Boolean isActive = true;

    private int year = Calendar.getInstance().getWeekYear();

    @PrePersist
    public void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (balance == null) balance = BigDecimal.ZERO;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }
}
