package com.erp.dto.salary;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class PayslipDocumentDTO {

    // ── Employee ───────────────────────────────────
    private String employeeNo;
    private String firstName;
    private String lastName;
    private String department;        // holds departmentName
    private String designation;
    private LocalDate dateOfJoining;
    private Integer workingDays;
    private Integer totalDays;
    private Integer leaveTaken;

    // ── Currency ───────────────────────────────────
    private String currencyCode;
    private String currencySymbol;
    private Boolean currencyConfigured;
    private String currencyWarning;

    // ── Payroll ────────────────────────────────────
    private String payrollCode;
    private LocalDate payPeriodStart;
    private LocalDate payPeriodEnd;
    private LocalDate payDate;

    // ── Line Items ─────────────────────────────────
    private List<LineItemDTO> earnings;
    private List<LineItemDTO> deductions;

    // ── Totals ─────────────────────────────────────
    private double grossPay;
    private double totalDeductions;
    private double netPayable;

    // ── Bank ───────────────────────────────────────
    private String bankName;
    private String bankBranch;
    private String accountNo;

    @Data
    public static class LineItemDTO {
        private String label;
        private double amount;
    }
}