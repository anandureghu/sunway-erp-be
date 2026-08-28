package com.erp.dto.salary;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Bulk benefits adjustment: raise selected pay components by a percentage for a set
 * of employees chosen by grade code, department, or a single employee.
 */
@Data
public class BenefitsAdjustmentRequestDTO {

    public enum Scope { GRADE_CODE, DEPARTMENT, EMPLOYEE }

    /** How the target employees are selected. */
    private Scope scope;

    /** Selector value for the chosen scope. */
    private String gradeCode;      // scope = GRADE_CODE (JobCode.salaryGrade, e.g. "G2")
    private Long departmentId;     // scope = DEPARTMENT
    private Long employeeId;       // scope = EMPLOYEE

    /** Percentage increase, e.g. 5 or 10 (must be > 0). */
    private BigDecimal percentage;

    /**
     * Which pay components to raise. Keys: BASIC, HOUSING, TRANSPORT, FOOD, TRAVEL, OTHER.
     * Empty/null means every allowance (everything except BASIC).
     */
    private Set<String> components;
}
