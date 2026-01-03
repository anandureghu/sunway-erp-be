package com.erp.dto.finance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalLineUpdateDTO {
    private Long debitAccount;
    private Long creditAccount;
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private Long departmentId;
    private Long projectId;
    private String currencyCode;
    private BigDecimal exchangeRate;
    private String description;
}
