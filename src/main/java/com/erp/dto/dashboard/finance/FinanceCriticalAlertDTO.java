package com.erp.dto.dashboard.finance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceCriticalAlertDTO {

    /** OVERDUE_RECEIVABLES, OVERDUE_PAYABLES, VENDOR_AGING, BUDGET_EXCEEDED */
    private String type;
    private String message;
    private long count;
    private BigDecimal amount;
}
