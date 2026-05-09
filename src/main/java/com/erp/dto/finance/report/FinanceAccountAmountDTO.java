package com.erp.dto.finance.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceAccountAmountDTO {

    private String accountName;
    private String accountCode;
    private BigDecimal amount;
}
