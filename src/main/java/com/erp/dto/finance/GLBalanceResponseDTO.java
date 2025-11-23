package com.erp.dto.finance;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class GLBalanceResponseDTO {
    private Long accountId;
    private String fiscalYear;

    private BigDecimal totalAssets;
    private BigDecimal totalLiabilities;
    private BigDecimal totalRevenue;
    private BigDecimal totalExpenses;
    private BigDecimal balance;
}
