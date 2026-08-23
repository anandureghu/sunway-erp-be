package com.erp.dto.hr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalTime;

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
    /** IANA timezone for attendance / company clocks; default Asia/Qatar. */
    private String timezone;
    /** Grace minutes after max shift before auto attendance check-out; null/0 = none. */
    private Integer maxShiftCheckoutGraceMinutes;

    /**
     * ERP UI session idle timeout (minutes). Session security — not attendance.
     * null/0 = Off; allowed values: 15, 20, 30.
     */
    private Integer sessionIdleTimeoutMinutes;

    // ---- Probation ----
    private Integer probationPeriodMonths;

    // ---- Overtime (Qatar labor-law defaults; editable) ----
    private BigDecimal otDayRateMultiplier;
    private BigDecimal otNightFridayHolidayRateMultiplier;
    private LocalTime otNightStartTime;
    private LocalTime otNightEndTime;
    private BigDecimal otMaxHoursPerDay;

    // ---- Statutory compensation defaults ----
    private BigDecimal minimumMonthlyWage;
    private BigDecimal defaultHousingAllowance;
    private BigDecimal defaultFoodAllowance;
}
