package com.erp.domain.finance;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;

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

    @Column(name = "account_code", unique = true)
    private String accountCode;

    @Column(name = "account_name")
    private String accountName;

    private String description;
    private String type; // asset, liability, expense, revenue, equity
    private String currency;
    private String status; // active, inactive

    @Column(name = "gl_account_class_type")
    private String glAccountClassType;

    @Column(name = "gl_account_type")
    private String glAccountType;

    @Column(precision = 18, scale = 2)
    private BigDecimal balance;

    @Column(name = "as_of_date")
    private Instant asOfDate;

    @ManyToOne
    @JoinColumn(name = "parent_id")
    private ChartOfAccounts parent;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // Getters and Setters
}
