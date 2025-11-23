package com.erp.dto.finance;

import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateGLBalanceDTO {
    private Long accountId;
    private String fiscalYear;
    private Instant accountingPeriodStart;
    private Instant accountingPeriodEnd;

    private BigDecimal totalAssets;
    private BigDecimal totalLiabilities;
    private BigDecimal totalRevenue;
    private BigDecimal totalExpenses;

    private Instant asOfDate;
}
