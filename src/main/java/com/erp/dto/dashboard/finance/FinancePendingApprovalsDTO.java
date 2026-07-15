package com.erp.dto.dashboard.finance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancePendingApprovalsDTO {

    private long purchaseRequisitions;
    private long purchaseOrders;
    private long paymentRequests;
    private long journalEntries;
}
