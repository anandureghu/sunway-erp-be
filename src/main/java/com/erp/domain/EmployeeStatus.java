package com.erp.domain;

public enum EmployeeStatus {
    ACTIVE,
    INACTIVE,
    ON_LEAVE,
    UNDER_PROBATION,
    RESIGNED,
    TERMINATED,
    RETIRED;

    /**
     * True when the employee has left the company or been deactivated. Such employees
     * cannot sign in and cannot be assigned as a department or division manager.
     */
    public boolean isDepartedOrInactive() {
        return this == TERMINATED
                || this == RESIGNED
                || this == RETIRED
                || this == INACTIVE;
    }
}
