package com.erp.domain.finance;

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
@Table(name = "gl_account_balances")
public class GLAccountBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    private String fiscalYear;

    private Instant accountingPeriodStart;
    private Instant accountingPeriodEnd;

    private BigDecimal totalAssets = BigDecimal.ZERO;
    private BigDecimal totalLiabilities = BigDecimal.ZERO;
    private BigDecimal totalRevenue = BigDecimal.ZERO;
    private BigDecimal totalExpenses = BigDecimal.ZERO;

    private BigDecimal balance = BigDecimal.ZERO;

    private Instant asOfDate;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    public void onCreate() {
        createdAt = Instant.now();
    }
}
