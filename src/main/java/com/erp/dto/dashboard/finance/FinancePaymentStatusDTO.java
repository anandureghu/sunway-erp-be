package com.erp.dto.dashboard.finance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancePaymentStatusDTO {

    private long paidCount;
    private long partiallyPaidCount;
    private long unpaidCount;
    private long totalCount;
}
