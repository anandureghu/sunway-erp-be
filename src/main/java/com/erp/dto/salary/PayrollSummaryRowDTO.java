package com.erp.dto.salary;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/** One row in the company-wide HR payroll-summary report (grouped by department). */
@Data
@Builder
public class PayrollSummaryRowDTO {
    private Long employeeId;
    private String employeeNo;
    private String employeeName;
    private String department;      // department name, or "Unassigned"

    private String payrollCode;
    private LocalDate payPeriodStart;
    private LocalDate payPeriodEnd;
    private LocalDate payDate;

    private double grossPay;
    private double totalDeductions;  // loans + loss of pay
    private double loanDeduction;
    private double lopAmount;
    private double overtimePay;
    private double endOfServiceCompensation;
    private double netPayable;

    private boolean finalSettlement; // exit settlement vs regular run
}
