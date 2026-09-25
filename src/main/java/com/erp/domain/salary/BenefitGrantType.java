package com.erp.domain.salary;

/** One-off benefits HR grants to an employee and pays through payroll. */
public enum BenefitGrantType {
    /** Once per calendar year per employee. */
    ANNUAL_TICKET("Annual Ticket"),
    BONUS("Bonus"),
    /** Requires a supporting document (receipt / invoice). */
    REIMBURSEMENT("Reimbursement");

    private final String label;

    BenefitGrantType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
