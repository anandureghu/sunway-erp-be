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
    private double endOfServiceCompensation;
    private boolean finalSettlement;
    private double grossPay;
    private PayrollAccountStatusDTO payrollAccount;
}