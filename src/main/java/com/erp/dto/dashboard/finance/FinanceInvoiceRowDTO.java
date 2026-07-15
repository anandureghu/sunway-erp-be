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
public class FinanceInvoiceRowDTO {

    private String invoiceId;
    private String party;
    private LocalDate dueDate;

    /** Negative when the invoice is not yet due. */
    private long daysOverdue;

    private BigDecimal amount;
    private BigDecimal outstanding;
}
