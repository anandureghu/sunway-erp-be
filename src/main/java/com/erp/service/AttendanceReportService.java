package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeTimesheet;
import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.timesheet.EmployeeMonthlyAttendanceDTO;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.EmployeeTimesheetRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.security.PermissionCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Company-wide attendance reporting for HR — the monthly worked-days rollup that
 * drives payroll. Row scope follows the caller's HR_REPORTS grant: a user with
 * the grant sees every employee in the company; anyone else (a regular employee)
 * sees only their own attendance, mirroring the Immigration expiry report.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceReportService {

    /** A day counts as "worked" once at least 6 hours are logged. */
    private static final long MIN_WORKED_MINUTES_FOR_DAY = 360L;

    private final EmployeeRepository employeeRepo;
    private final EmployeeTimesheetRepository timesheetRepo;
    private final AuthContext authContext;
    private final PermissionCheckService permissionCheck;

    public List<EmployeeMonthlyAttendanceDTO> getMonthlySummary(int year, int month) {
        Long companyId = authContext.getCurrentCompanyId();
        if (companyId == null) {
            throw new AccessDeniedException("No company context for the current user");
        }
        return summarize(resolveEmployees(companyId), year, month);
    }

    /**
     * Company-wide rollup for every employee, ignoring the per-caller scope.
     * Used by the month-archive snapshot, which is always company-wide.
     */
    public List<EmployeeMonthlyAttendanceDTO> computeCompanySummary(Long companyId, int year, int month) {
        return summarize(employeeRepo.findByCompany_IdOrderByCreatedAtDesc(companyId), year, month);
    }

    private List<EmployeeMonthlyAttendanceDTO> summarize(List<Employee> employees, int year, int month) {
        if (employees.isEmpty()) {
            return List.of();
        }

        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        List<Long> ids = employees.stream().map(Employee::getId).toList();
        Map<Long, List<EmployeeTimesheet>> byEmployee = timesheetRepo
                .findByEmployeeIdInAndAttendanceDateBetween(ids, start, end)
                .stream()
                .collect(Collectors.groupingBy(EmployeeTimesheet::getEmployeeId));

        LocalDate today = LocalDate.now();

        List<EmployeeMonthlyAttendanceDTO> rows = new ArrayList<>();
        for (Employee e : employees) {
            List<EmployeeTimesheet> records = byEmployee.getOrDefault(e.getId(), List.of());

            int daysRecorded = records.size();
            int daysPresent = (int) records.stream().filter(this::isPresent).count();
            long totalMinutes = records.stream().mapToLong(this::resolveWorkedMinutes).sum();
            double totalHours = Math.round(totalMinutes / 60.0 * 10.0) / 10.0;

            // Today's live status (present only when the queried month is current).
            EmployeeTimesheet todayRec = records.stream()
                    .filter(r -> today.equals(r.getAttendanceDate()))
                    .findFirst()
                    .orElse(null);
            String todayStatus = todayRec != null && todayRec.getStatus() != null
                    ? todayRec.getStatus().name()
                    : "NOT_CHECKED_IN";
            double todayHours = todayRec != null
                    ? Math.round(resolveWorkedMinutes(todayRec) / 60.0 * 10.0) / 10.0
                    : 0.0;

            rows.add(EmployeeMonthlyAttendanceDTO.builder()
                    .employeeId(e.getId())
                    .employeeNo(e.getEmployeeNo())
                    .employeeName(fullName(e))
                    .department(e.getDepartment() != null ? e.getDepartment().getDepartmentName() : null)
                    .daysRecorded(daysRecorded)
                    .daysPresent(daysPresent)
                    .totalHours(totalHours)
                    .todayStatus(todayStatus)
                    .todayCheckIn(todayRec != null ? todayRec.getCheckInTime() : null)
                    .todayCheckOut(todayRec != null ? todayRec.getCheckOutTime() : null)
                    .todayHours(todayHours)
                    .build());
        }

        rows.sort(Comparator.comparing(r -> r.getEmployeeName() == null ? "" : r.getEmployeeName()));
        return rows;
    }

    /** Company-wide when the caller can view HR reports; otherwise just themselves. */
    private List<Employee> resolveEmployees(Long companyId) {
        if (canViewAll()) {
            return employeeRepo.findByCompany_IdOrderByCreatedAtDesc(companyId);
        }
        Long employeeId = authContext.getCurrentEmployeeId();
        if (employeeId == null) {
            return List.of();
        }
        return employeeRepo.findById(employeeId)
                .filter(e -> e.getCompany() != null && companyId.equals(e.getCompany().getId()))
                .map(List::of)
                .orElseGet(List::of);
    }

    private boolean canViewAll() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return permissionCheck.hasAny(auth, AppModule.HR_REPORTS, AppAction.VIEW_ALL, AppAction.VIEW_OWN);
    }

    private boolean isPresent(EmployeeTimesheet t) {
        return resolveWorkedMinutes(t) >= MIN_WORKED_MINUTES_FOR_DAY;
    }

    private long resolveWorkedMinutes(EmployeeTimesheet t) {
        if (t.getWorkedMinutes() != null) {
            return t.getWorkedMinutes();
        }
        if (t.getCheckInTime() != null && t.getCheckOutTime() != null) {
            return Duration.between(t.getCheckInTime(), t.getCheckOutTime()).toMinutes();
        }
        return 0L;
    }

    private String fullName(Employee e) {
        String f = e.getFirstName() == null ? "" : e.getFirstName();
        String l = e.getLastName() == null ? "" : e.getLastName();
        return (f + " " + l).trim();
    }
}
