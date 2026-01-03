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
    private Long debitAccountId;
    private Long creditAccountId;
    private String debitAccountName;
    private String creditAccountName;
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private Long departmentId;
    private Long departmentName;
    private Long projectId;
    private String currencyCode;
    private BigDecimal exchangeRate;
    private String description;
}
