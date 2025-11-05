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
@Table(name = "gl_account_balances")
public class GLAccountBalances {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "account_id")
    private ChartOfAccounts account;

    @Column(name = "fiscal_year")
    private String fiscalYear;

    @Column(name = "accounting_period_start")
    private Instant accountingPeriodStart;

    @Column(name = "accounting_period_end")
    private Instant accountingPeriodEnd;

    @Column(precision = 18, scale = 2)
    private BigDecimal totalAssets;

    @Column(precision = 18, scale = 2)
    private BigDecimal totalLiabilities;

    @Column(precision = 18, scale = 2)
    private BigDecimal totalRevenue;

    @Column(precision = 18, scale = 2)
    private BigDecimal totalExpenses;

    @Column(precision = 18, scale = 2)
    private BigDecimal balance;

    @Column(name = "as_of_date")
    private Instant asOfDate;

    @Column(name = "created_at")
    private Instant createdAt;
}
