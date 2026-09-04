package com.erp.domain.enums;

/**
 * Approval state of a job code. A newly created code is PENDING_APPROVAL and cannot be
 * assigned to an employee until an HR manager APPROVES it (or REJECTS it).
 */
public enum JobCodeStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED
}
