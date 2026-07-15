package com.erp.dto.dashboard.finance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceTransactionRowDTO {

    private String transactionCode;
    private String transactionType;
    private String description;
    private LocalDate transactionDate;
    private BigDecimal amount;
}
