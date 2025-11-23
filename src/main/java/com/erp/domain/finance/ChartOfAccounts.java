package com.erp.domain.finance;

import com.erp.domain.hr.Company;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "chart_of_accounts")
public class ChartOfAccounts {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_code", nullable = false, unique = true, length = 64)
    private String accountCode;

    @Column(name = "account_name", nullable = false, length = 255)
    private String accountName;

    private String description;

    // asset, liability, income, expense, equity
    private String type;

    @ManyToOne
    @JoinColumn(name = "parent_id")
    private ChartOfAccounts parent;

    private String currency;

    // active / inactive
    private String status;

    @Column(name = "gl_account_class_type_key")
    private String glAccountClassTypeKey;

    @Column(name = "gl_account_type")
    private String glAccountType;

    private BigDecimal balance;

    @Column(name = "as_of_date")
    private Instant asOfDate;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @ManyToOne
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

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
