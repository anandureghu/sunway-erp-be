package com.erp.dto.finance;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalLineDTO {
    private Long id;
    private Long accountId;
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private Long departmentId;
    private Long projectId;
    private String currencyCode;
    private BigDecimal exchangeRate;
    private String description;
}
