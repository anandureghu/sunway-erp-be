package com.erp.domain;

public enum LeaveStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
    /** Employee has returned to office; actual leave days have been deducted. */
    COMPLETED
}