package com.erp.dto.dashboard.hr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrDashboardKpisDTO {

    private long totalEmployees;
    private long activeEmployees;
    private long employeesOnLeave;
    private long newJoinersThisMonth;
    private long resignationsThisMonth;
    private long qidExpiring30d;
    private long contractsExpiring30d;

    /** Stubbed to 0 — no probation-end field exists on Employee/EmployeeCurrentJob yet. */
    private long probationEndingSoon;
}
