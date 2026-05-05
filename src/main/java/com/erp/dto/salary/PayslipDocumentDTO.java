package com.erp.dto.salary;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;
@Data
public class PayslipDocumentDTO {

    private String employeeNo;
    private String firstName;
    private String lastName;
    private String department;
    private String designation;
    private LocalDate dateOfJoining;

    private Integer workingDays;
    private Integer totalDays;
    private Integer leaveTaken;

    private Double workedDays;
    private Double workedHours;
    private Double paidLeaveDays;
    private Double unpaidLeaveDays;
    private Double payableDays;
    private Double lopDays;
    private Double lopAmount;

    private String currencyCode;
    private String currencySymbol;
    private Boolean currencyConfigured;
    private String currencyWarning;

    private String payrollCode;
    private LocalDate payPeriodStart;
    private LocalDate payPeriodEnd;
    private LocalDate payDate;

    private List<LineItemDTO> earnings;
    private List<LineItemDTO> deductions;

    private double grossPay;
    private double totalDeductions;
    private double netPayable;

    private String bankName;
    private String bankBranch;
    private String accountNo;

    @Data
    public static class LineItemDTO {
        private String label;
        private double amount;
    }
}