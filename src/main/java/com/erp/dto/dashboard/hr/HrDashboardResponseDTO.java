package com.erp.dto.dashboard.hr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrDashboardResponseDTO {

    private HrDashboardKpisDTO kpis;

    private HrPendingApprovalsDTO pendingApprovals;

    private HrComplianceAlertsDTO complianceAlerts;

    private HrWorkforceStatusDTO workforceStatusToday;

    private List<HrDepartmentDistributionDTO> employeesByDepartment;

    private HrLeaveSummaryDTO leaveSummaryThisMonth;

    private List<HrTrendPointDTO> leaveTrendLast12Months;

    private List<HrUpcomingEventDTO> upcomingHrEvents;

    private List<HrActivityItemDTO> recentHrActivities;

    private HrDocumentsExpiringDTO documentsExpiring;

    private Instant generatedAt;
}
