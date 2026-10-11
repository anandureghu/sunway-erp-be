package com.erp.domain.enums;

/**
 * Why a job code is ON_HOLD — decides how the hold is closed.
 * <ul>
 *   <li>PENDING_APPROVAL — the department needs approval to fill the position. Closed only
 *       by an approver: Approve (back to VACANT) or Reject (VACANT and deactivated).</li>
 *   <li>RESERVED — the position is kept for a specific person. Closed by Release (VACANT).</li>
 * </ul>
 */
public enum JobCodeHoldReason {
    PENDING_APPROVAL,
    RESERVED
}
