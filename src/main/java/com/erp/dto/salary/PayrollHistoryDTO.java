package com.erp.dto.salary;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class PayrollHistoryDTO {

    private String payrollCode;

    private LocalDate payPeriodStart;
    private LocalDate payPeriodEnd;
    private LocalDate payDate;

    private Double grossPay;

    private Double loanDeduction;     // ✅ ADD THIS
    private Double totalDeductions;   // ✅ ADD THIS (future-proof)

    private Double netPayable;
}
