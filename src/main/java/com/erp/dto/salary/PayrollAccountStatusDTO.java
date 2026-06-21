package com.erp.dto.salary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollAccountStatusDTO {

    /** NOT_CONFIGURED, READY, or INSUFFICIENT */
    private String status;

    private boolean configured;
    private Long debitAccountId;
    private String debitAccountCode;
    private String debitAccountName;
    private double availableBalance;
    private double payrollGrossAmount;
    private boolean sufficientFunds;
}
