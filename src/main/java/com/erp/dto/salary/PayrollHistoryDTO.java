package com.erp.dto.salary;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Setter
@Getter
public class PayrollHistoryDTO {

    private String payrollCode;
    private LocalDate payPeriodStart;
    private LocalDate payPeriodEnd;
    private LocalDate payDate;

    private Double grossPay;
    private Double deductions;
    private Double netPayable;

    private String bankName;
    private String bankAccount;


}
