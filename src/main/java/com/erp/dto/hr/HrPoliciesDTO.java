package com.erp.dto.hr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Focused payload for the company's HR policies — annual leave accrual and
 * end-of-service retirement compensation. Mirrors the `/accounting-defaults`
 * and `/invoice-branding` pattern: small endpoint, no need to round-trip the
 * full company record.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrPoliciesDTO {

    // ---- Leave accrual ----
    private Boolean annualLeaveAccrualEnabled;
    private BigDecimal annualLeaveAccrualDaysPerMonth;
    private Integer minServiceMonthsForAnnualLeave;

    // ---- Retirement compensation ----
    private Boolean retirementCompensationEnabled;
    private BigDecimal retirementCompensationMonthsPerYear;

    // ---- Loan eligibility & repayment ----
    private Boolean loanPolicyEnabled;
    private Integer loanMinServiceDays;
    private Integer loanMaxRepaymentMonths;

    // ---- Attendance & working hours ----
    private BigDecimal standardWorkingHoursPerDay;
    private Boolean requireCheckIn;
}
