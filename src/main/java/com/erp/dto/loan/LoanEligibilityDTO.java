package com.erp.dto.loan;

/**
 * Result of a loan-eligibility pre-check so the UI can block a "Request Loan" before
 * the form is filled in when the employee doesn't yet qualify under company policy.
 */
public record LoanEligibilityDTO(
        boolean eligible,
        String reason,
        Integer minServiceDays,
        Long daysOfService,
        Integer maxRepaymentMonths) {
}
