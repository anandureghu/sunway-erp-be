package com.erp.dto.hr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Snapshot of an employee's retirement / end-of-service compensation entitlement
 * given the company's configured policy:
 *   compensationAmount = basicSalary * monthsPerYear * yearsOfService.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetirementCompensationDTO {

    private Long employeeId;
    private String employeeName;
    private LocalDate joinDate;
    private LocalDate calculatedOn;

    private BigDecimal yearsOfService;
    private BigDecimal basicSalary;

    /** Months of basic salary accrued per completed year of service. */
    private BigDecimal monthsPerYear;

    /** Total months of basic salary accrued for the full service period. */
    private BigDecimal accruedMonths;

    /** Final compensation amount = basicSalary * accruedMonths. */
    private BigDecimal compensationAmount;

    private String currencyCode;
}
