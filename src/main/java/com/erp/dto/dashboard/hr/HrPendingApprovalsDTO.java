package com.erp.dto.dashboard.hr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrPendingApprovalsDTO {

    private long leaveRequests;

    /** Stubbed to 0 — no Overtime entity/table exists yet. */
    private long overtimeRequests;

    /** Stubbed to 0 — no Employee Transfer entity/table exists yet. */
    private long employeeTransfers;

    /** Stubbed to 0 — no pending-registration workflow/status exists on Employee yet. */
    private long employeeRegistrations;

    private long contractRenewals;
}
