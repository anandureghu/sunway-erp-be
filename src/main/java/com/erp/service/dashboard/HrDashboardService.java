package com.erp.service.dashboard;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeLeave;
import com.erp.domain.EmployeeStatus;
import com.erp.domain.EmployeeTimesheet;
import com.erp.domain.LeaveStatus;
import com.erp.domain.Passport;
import com.erp.domain.ResidencePermit;
import com.erp.domain.TimesheetStatus;
import com.erp.domain.enums.ContractStatus;
import com.erp.domain.hr.Contract;
import com.erp.dto.dashboard.hr.HrActivityItemDTO;
import com.erp.dto.dashboard.hr.HrComplianceAlertsDTO;
import com.erp.dto.dashboard.hr.HrDashboardKpisDTO;
import com.erp.dto.dashboard.hr.HrDashboardResponseDTO;
import com.erp.dto.dashboard.hr.HrDepartmentDistributionDTO;
import com.erp.dto.dashboard.hr.HrDocumentsExpiringDTO;
import com.erp.dto.dashboard.hr.HrLeaveSummaryDTO;
import com.erp.dto.dashboard.hr.HrPendingApprovalsDTO;
import com.erp.dto.dashboard.hr.HrTrendPointDTO;
import com.erp.dto.dashboard.hr.HrUpcomingEventDTO;
import com.erp.dto.dashboard.hr.HrWorkforceStatusDTO;
import com.erp.repo.EmployeeLeaveRepository;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.EmployeeTimesheetRepository;
import com.erp.repo.PassportRepository;
import com.erp.repo.ResidencePermitRepository;
import com.erp.repo.hr.ContractRepository;
import com.erp.security.context.AuthContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aggregates existing HR data into the shape the HR Manager dashboard widgets need.
 * Overtime requests and employee transfers have no backing entity yet, so those
 * counts are stubbed to 0 (see {@link HrPendingApprovalsDTO}).
 */
@Service
@Transactional(readOnly = true)
public class HrDashboardService {

    private static final int EXPIRY_WINDOW_DAYS = 30;
    private static final int TREND_MONTHS = 12;
    private static final int UPCOMING_EVENTS_LIMIT = 10;
    private static final int RECENT_ACTIVITIES_LIMIT = 10;
    private static final int RECENT_LEAVE_WINDOW_DAYS = 14;

    private final EmployeeRepository employeeRepo;
    private final EmployeeLeaveRepository employeeLeaveRepo;
    private final PassportRepository passportRepo;
    private final ResidencePermitRepository residencePermitRepo;
    private final ContractRepository contractRepo;
    private final EmployeeTimesheetRepository timesheetRepo;
    private final AuthContext auth;

    public HrDashboardService(
            EmployeeRepository employeeRepo,
            EmployeeLeaveRepository employeeLeaveRepo,
            PassportRepository passportRepo,
            ResidencePermitRepository residencePermitRepo,
            ContractRepository contractRepo,
            EmployeeTimesheetRepository timesheetRepo,
            AuthContext auth
    ) {
        this.employeeRepo = employeeRepo;
        this.employeeLeaveRepo = employeeLeaveRepo;
        this.passportRepo = passportRepo;
        this.residencePermitRepo = residencePermitRepo;
        this.contractRepo = contractRepo;
        this.timesheetRepo = timesheetRepo;
        this.auth = auth;
    }

    public HrDashboardResponseDTO build() {
        Long companyId = auth.getCurrentCompanyId();
        if (companyId == null) {
            throw new RuntimeException("User is not associated with a company");
        }

        LocalDate today = LocalDate.now();
        LocalDate startOfMonth = today.withDayOfMonth(1);
        LocalDate endOfMonth = startOfMonth.plusMonths(1).minusDays(1);
        LocalDate expiryCutoff = today.plusDays(EXPIRY_WINDOW_DAYS);

        List<ResidencePermit> expiringPermits = filterFromToday(
                residencePermitRepo.findByEmployee_Company_IdAndEndDateLessThanEqual(companyId, expiryCutoff),
                ResidencePermit::getEndDate, today);
        List<Passport> expiringPassports = filterFromToday(
                passportRepo.findByEmployee_Company_IdAndExpiryDateLessThanEqual(companyId, expiryCutoff),
                Passport::getExpiryDate, today);
        List<Contract> expiringContracts = filterFromToday(
                contractRepo.findByCompany_IdAndDeletedFalseAndExpirationDateLessThanEqual(companyId, expiryCutoff),
                Contract::getExpirationDate, today);

        Set<Long> onLeaveTodayIds = new HashSet<>(
                employeeLeaveRepo.findEmployeeIdsOnApprovedLeaveForCompany(companyId, today));

        HrDashboardKpisDTO kpis = buildKpis(
                companyId, today, startOfMonth, expiringPermits, expiringContracts, onLeaveTodayIds);

        HrPendingApprovalsDTO pendingApprovals = buildPendingApprovals(companyId);

        HrComplianceAlertsDTO complianceAlerts = HrComplianceAlertsDTO.builder()
                .qidExpiring30d(expiringPermits.size())
                .passportExpiring30d(expiringPassports.size())
                .vaccinationExpiring30d(0)
                .contractsExpiring30d(expiringContracts.size())
                .probationEndingSoon(0)
                .build();

        HrWorkforceStatusDTO workforceStatusToday = buildWorkforceStatus(companyId, today, onLeaveTodayIds);

        List<HrDepartmentDistributionDTO> employeesByDepartment = buildDepartmentDistribution(companyId);

        HrLeaveSummaryDTO leaveSummaryThisMonth = buildLeaveSummary(
                companyId, startOfMonth, endOfMonth, onLeaveTodayIds.size());

        List<HrTrendPointDTO> leaveTrend = buildLeaveTrend(companyId, today);

        List<HrUpcomingEventDTO> upcomingEvents = buildUpcomingEvents(
                companyId, today, expiringPermits, expiringPassports, expiringContracts);

        List<HrActivityItemDTO> recentActivities = buildRecentActivities(companyId, today);

        HrDocumentsExpiringDTO documentsExpiring = HrDocumentsExpiringDTO.builder()
                .qidExpiring(expiringPermits.size())
                .passportExpiring(expiringPassports.size())
                // ResidencePermit covers QID/residence; do not double-count under visaExpiring.
                .visaExpiring(0)
                .contractsExpiring(expiringContracts.size())
                .otherDocsExpiring(0)
                .build();

        return HrDashboardResponseDTO.builder()
                .kpis(kpis)
                .pendingApprovals(pendingApprovals)
                .complianceAlerts(complianceAlerts)
                .workforceStatusToday(workforceStatusToday)
                .employeesByDepartment(employeesByDepartment)
                .leaveSummaryThisMonth(leaveSummaryThisMonth)
                .leaveTrendLast12Months(leaveTrend)
                .upcomingHrEvents(upcomingEvents)
                .recentHrActivities(recentActivities)
                .documentsExpiring(documentsExpiring)
                .generatedAt(Instant.now())
                .build();
    }

    private HrDashboardKpisDTO buildKpis(
            Long companyId,
            LocalDate today,
            LocalDate startOfMonth,
            List<ResidencePermit> expiringPermits,
            List<Contract> expiringContracts,
            Set<Long> onLeaveTodayIds) {
        Instant startOfMonthInstant = startOfMonth.atStartOfDay(ZoneId.systemDefault()).toInstant();

        return HrDashboardKpisDTO.builder()
                .totalEmployees(employeeRepo.countByCompany_Id(companyId))
                .activeEmployees(employeeRepo.countByCompany_IdAndStatus(companyId, EmployeeStatus.ACTIVE))
                .employeesOnLeave(onLeaveTodayIds.size())
                .newJoinersThisMonth(employeeRepo.countByCompany_IdAndJoinDateBetween(companyId, startOfMonth, today))
                .resignationsThisMonth(employeeRepo.countByCompany_IdAndStatusAndUpdatedAtBetween(
                        companyId, EmployeeStatus.RESIGNED, startOfMonthInstant, Instant.now()))
                .qidExpiring30d(expiringPermits.size())
                .contractsExpiring30d(expiringContracts.size())
                .probationEndingSoon(0)
                .build();
    }

    private HrPendingApprovalsDTO buildPendingApprovals(Long companyId) {
        long leaveRequests = employeeLeaveRepo
                .findByEmployeeCompany_IdAndLeaveStatusOrderByDateReportedDesc(companyId, LeaveStatus.PENDING)
                .size();
        long contractRenewals = contractRepo.countByCompany_IdAndDeletedFalseAndStatus(
                companyId, ContractStatus.DRAFT);

        return HrPendingApprovalsDTO.builder()
                .leaveRequests(leaveRequests)
                .overtimeRequests(0)
                .employeeTransfers(0)
                .employeeRegistrations(0)
                .contractRenewals(contractRenewals)
                .build();
    }

    private HrWorkforceStatusDTO buildWorkforceStatus(Long companyId, LocalDate today, Set<Long> onLeaveTodayIds) {
        List<Employee> activeEmployees = employeeRepo.findByCompany_IdAndStatus(companyId, EmployeeStatus.ACTIVE);
        List<Long> activeIds = activeEmployees.stream().map(Employee::getId).toList();

        Map<Long, TimesheetStatus> statusByEmployee = new HashMap<>();
        for (EmployeeTimesheet ts : timesheetRepo.findByEmployeeIdInAndAttendanceDateBetween(activeIds, today, today)) {
            statusByEmployee.put(ts.getEmployeeId(), ts.getStatus());
        }

        long present = 0;
        for (Long id : activeIds) {
            if (onLeaveTodayIds.contains(id)) continue;
            TimesheetStatus status = statusByEmployee.get(id);
            if (status == TimesheetStatus.CHECKED_IN || status == TimesheetStatus.CHECKED_OUT) {
                present++;
            }
        }

        long onLeave = activeIds.stream().filter(onLeaveTodayIds::contains).count();
        long total = activeIds.size();
        long absent = Math.max(0, total - present - onLeave);

        return HrWorkforceStatusDTO.builder()
                .present(present)
                .onLeave(onLeave)
                .absent(absent)
                .total(total)
                .build();
    }

    private List<HrDepartmentDistributionDTO> buildDepartmentDistribution(Long companyId) {
        List<Object[]> rows = employeeRepo.countByDepartment(companyId);
        long total = rows.stream().mapToLong(r -> ((Number) r[2]).longValue()).sum();

        List<HrDepartmentDistributionDTO> out = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            long count = ((Number) row[2]).longValue();
            BigDecimal percent = total > 0
                    ? BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            out.add(HrDepartmentDistributionDTO.builder()
                    .departmentId(((Number) row[0]).longValue())
                    .departmentName((String) row[1])
                    .employeeCount(count)
                    .percent(percent)
                    .build());
        }
        return out;
    }

    private HrLeaveSummaryDTO buildLeaveSummary(
            Long companyId, LocalDate startOfMonth, LocalDate endOfMonth, long onLeaveToday) {
        List<EmployeeLeave> leaves = employeeLeaveRepo
                .findByEmployee_Company_IdAndDateReportedBetween(companyId, startOfMonth, endOfMonth);

        long approved = leaves.stream().filter(l -> l.getLeaveStatus() == LeaveStatus.APPROVED).count();
        long pending = leaves.stream().filter(l -> l.getLeaveStatus() == LeaveStatus.PENDING).count();
        long rejected = leaves.stream().filter(l -> l.getLeaveStatus() == LeaveStatus.REJECTED).count();

        return HrLeaveSummaryDTO.builder()
                .totalRequests(leaves.size())
                .approved(approved)
                .pending(pending)
                .rejected(rejected)
                .onLeaveToday(onLeaveToday)
                .build();
    }

    private List<HrTrendPointDTO> buildLeaveTrend(Long companyId, LocalDate today) {
        LocalDate from = today.minusMonths(TREND_MONTHS - 1L).withDayOfMonth(1);
        List<Object[]> rows = employeeLeaveRepo.monthlyRequestCounts(companyId, from, today);

        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            counts.put(formatYearMonth(year, month), ((Number) row[2]).longValue());
        }

        List<HrTrendPointDTO> out = new ArrayList<>(TREND_MONTHS);
        LocalDate cursor = from;
        for (int i = 0; i < TREND_MONTHS; i++) {
            String label = formatYearMonth(cursor.getYear(), cursor.getMonthValue());
            out.add(HrTrendPointDTO.builder()
                    .yearMonth(label)
                    .count(counts.getOrDefault(label, 0L))
                    .build());
            cursor = cursor.plusMonths(1);
        }
        return out;
    }

    private List<HrUpcomingEventDTO> buildUpcomingEvents(
            Long companyId,
            LocalDate today,
            List<ResidencePermit> expiringPermits,
            List<Passport> expiringPassports,
            List<Contract> expiringContracts) {
        List<HrUpcomingEventDTO> events = new ArrayList<>();

        for (ResidencePermit p : expiringPermits) {
            events.add(event("QID_EXPIRY", p.getEmployee(), p.getEndDate(), today));
        }
        for (Passport p : expiringPassports) {
            events.add(event("PASSPORT_EXPIRY", p.getEmployee(), p.getExpiryDate(), today));
        }
        for (Contract c : expiringContracts) {
            events.add(event("CONTRACT_EXPIRY", c.getEmployee(), c.getExpirationDate(), today));
        }

        LocalDate cutoff = today.plusDays(EXPIRY_WINDOW_DAYS);
        for (Employee e : employeeRepo.findByCompany_IdAndStatus(companyId, EmployeeStatus.ACTIVE)) {
            LocalDate joinDate = e.getJoinDate();
            if (joinDate == null) continue;
            LocalDate anniversary = nextAnniversary(joinDate, today);
            if (!anniversary.isAfter(cutoff)) {
                events.add(event("WORK_ANNIVERSARY", e, anniversary, today));
            }
        }

        return events.stream()
                .sorted(Comparator.comparingLong(HrUpcomingEventDTO::getDaysLeft))
                .limit(UPCOMING_EVENTS_LIMIT)
                .toList();
    }

    private static LocalDate nextAnniversary(LocalDate joinDate, LocalDate today) {
        MonthDay monthDay = MonthDay.from(joinDate);
        LocalDate thisYear = monthDay.atYear(today.getYear());
        return thisYear.isBefore(today) ? monthDay.atYear(today.getYear() + 1) : thisYear;
    }

    private static HrUpcomingEventDTO event(String type, Employee employee, LocalDate date, LocalDate today) {
        return HrUpcomingEventDTO.builder()
                .type(type)
                .employeeId(employee != null ? employee.getId() : null)
                .employeeName(employeeName(employee))
                .eventDate(date)
                .daysLeft(date == null ? 0L : ChronoUnit.DAYS.between(today, date))
                .build();
    }

    private List<HrActivityItemDTO> buildRecentActivities(Long companyId, LocalDate today) {
        List<HrActivityItemDTO> activities = new ArrayList<>();

        LocalDate recentFrom = today.minusDays(RECENT_LEAVE_WINDOW_DAYS);
        for (EmployeeLeave leave : employeeLeaveRepo
                .findByEmployee_Company_IdAndDateReportedBetween(companyId, recentFrom, today)) {
            activities.add(HrActivityItemDTO.builder()
                    .description(leave.getLeaveType() + " leave request submitted")
                    .employeeName(employeeName(leave.getEmployee()))
                    .occurredAt(toInstant(leave.getDateReported()))
                    .build());
        }

        for (Employee e : employeeRepo.findByCompany_IdOrderByCreatedAtDesc(companyId).stream()
                .limit(5).toList()) {
            activities.add(HrActivityItemDTO.builder()
                    .description("New employee added")
                    .employeeName(employeeName(e))
                    .occurredAt(e.getCreatedAt())
                    .build());
        }

        for (Contract c : contractRepo.findTop5ByCompany_IdAndDeletedFalseOrderByUpdatedAtDesc(companyId)) {
            activities.add(HrActivityItemDTO.builder()
                    .description("Contract " + c.getContractCode() + " updated")
                    .employeeName(employeeName(c.getEmployee()))
                    .occurredAt(toInstant(c.getUpdatedAt() != null ? c.getUpdatedAt() : c.getCreatedAt()))
                    .build());
        }

        return activities.stream()
                .filter(a -> a.getOccurredAt() != null)
                .sorted(Comparator.comparing(HrActivityItemDTO::getOccurredAt).reversed())
                .limit(RECENT_ACTIVITIES_LIMIT)
                .toList();
    }

    private static Instant toInstant(LocalDate date) {
        return date == null ? null : date.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private static Instant toInstant(java.time.LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(ZoneId.systemDefault()).toInstant();
    }

    private static String employeeName(Employee employee) {
        if (employee == null) return null;
        String first = employee.getFirstName() == null ? "" : employee.getFirstName();
        String last = employee.getLastName() == null ? "" : employee.getLastName();
        return (first + " " + last).trim();
    }

    private static String formatYearMonth(int year, int month) {
        return String.format("%04d-%02d", year, month);
    }

    private interface DateExtractor<T> {
        LocalDate apply(T t);
    }

    private static <T> List<T> filterFromToday(List<T> items, DateExtractor<T> dateExtractor, LocalDate today) {
        List<T> out = new ArrayList<>();
        for (T item : items) {
            LocalDate date = dateExtractor.apply(item);
            if (date != null && !date.isBefore(today)) {
                out.add(item);
            }
        }
        return out;
    }
}
