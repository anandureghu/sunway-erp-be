package com.erp.dto.salary;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PayrollPreviewDTO {

    private double monthlyGross;
    private int workingDays;
    private double perDaySalary;
    private double workedHours;
    private double workedDays;
    private double paidLeaveDays;
    private double unpaidLeaveDays;
    private double payableDays;
    private double lopDays;
    private double lopAmount;
    private double loanDeduction;
    private double totalDeductions;
    private double netPayable;
    private double earnedGrossPay;
    private double overtimePay;
    private double endOfServiceCompensation;
    private boolean finalSettlement;
    private double grossPay;
    private PayrollAccountStatusDTO payrollAccount;
    /** Benefit grants (annual ticket, bonus, reimbursement) this run will pay. */
    private double benefitsAmount;
    /** Calendar months the pay period covers (3.0 for Jun 1 – Aug 31). */
    private double periodMonths;
}