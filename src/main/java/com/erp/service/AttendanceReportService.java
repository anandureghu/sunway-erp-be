package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeOvertimeOverride;
import com.erp.domain.EmployeeTimesheet;
import com.erp.domain.hr.Company;
import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.timesheet.EmployeeMonthlyAttendanceDTO;
import com.erp.repo.EmployeeOvertimeOverrideRepository;
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

import java.time.DayOfWeek;
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

    private final EmployeeRepository employeeRepo;
    private final EmployeeTimesheetRepository timesheetRepo;
    private final EmployeeOvertimeOverrideRepository overtimeOverrideRepo;
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

    /**
     * Set (or clear) the manually-entered overtime for one employee for one month.
     * Only meaningful for no-punch companies; gated by the company-wide HR reports grant.
     */
    @Transactional
    public void setOvertimeOverride(Long employeeId, int year, int month, double overtimeHours) {
        Long companyId = authContext.getCurrentCompanyId();
        if (companyId == null) {
            throw new AccessDeniedException("No company context for the current user");
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!permissionCheck.hasAny(auth, AppModule.HR_REPORTS, AppAction.VIEW_ALL)) {
            throw new AccessDeniedException("HR reports permission is required to edit overtime");
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Invalid month");
        }
        if (overtimeHours < 0) {
            throw new IllegalArgumentException("Overtime hours cannot be negative");
        }
        double rounded = Math.round(overtimeHours * 10.0) / 10.0;

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));
        if (employee.getCompany() == null || !companyId.equals(employee.getCompany().getId())) {
            throw new AccessDeniedException("Employee belongs to another company");
        }

        EmployeeOvertimeOverride override = overtimeOverrideRepo
                .findByEmployee_IdAndYearAndMonth(employeeId, year, month)
                .orElseGet(() -> {
                    EmployeeOvertimeOverride o = new EmployeeOvertimeOverride();
                    o.setEmployee(employee);
                    o.setCompany(employee.getCompany());
                    o.setYear(year);
                    o.setMonth(month);
                    return o;
                });
        override.setOvertimeHours(rounded);
        overtimeOverrideRepo.save(override);
    }

    private List<EmployeeMonthlyAttendanceDTO> summarize(List<Employee> employees, int year, int month) {
        if (employees.isEmpty()) {
            return List.of();
        }

        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        LocalDate today = LocalDate.now();

        // Company attendance policy (all rows here belong to one company).
        double stdHours = 6.0;
        double otMaxHours = 2.0;
        boolean requireCheckIn = true;
        try {
            Company c = employees.get(0).getCompany();
            if (c != null) {
                if (c.getStandardWorkingHoursPerDay() != null) {
                    stdHours = c.getStandardWorkingHoursPerDay().doubleValue();
                }
                if (c.getOtMaxHoursPerDay() != null) {
                    otMaxHours = c.getOtMaxHoursPerDay().doubleValue();
                }
                requireCheckIn = c.isRequireCheckIn();
            }
        } catch (Exception ignored) {
            // lazy company not loadable — use defaults
        }
        final long minMinutes = Math.round(stdHours * 60.0);
        final long otMaxMinutes = Math.round(otMaxHours * 60.0);

        // No-punch companies: every weekday up to today is a full standard day.
        if (!requireCheckIn) {
            int workingDays = countWorkingDaysUpToToday(year, month);
            double regularHours = Math.round(workingDays * stdHours * 10.0) / 10.0;
            boolean todayIsWorkday = ym.equals(YearMonth.from(today)) && isWeekday(today);
            // Overtime can't be derived without punches — it's whatever HR keyed in for
            // this month (nothing by default). Regular hours stay full; overtime adds on top.
            Map<Long, Double> overrides = overtimeOverrideRepo
                    .findByEmployee_IdInAndYearAndMonth(
                            employees.stream().map(Employee::getId).toList(), year, month)
                    .stream()
                    .collect(Collectors.toMap(
                            o -> o.getEmployee().getId(),
                            EmployeeOvertimeOverride::getOvertimeHours,
                            (a, b) -> b));
            List<EmployeeMonthlyAttendanceDTO> autoRows = new ArrayList<>();
            for (Employee e : employees) {
                double overtimeHours = Math.round(overrides.getOrDefault(e.getId(), 0.0) * 10.0) / 10.0;
                autoRows.add(EmployeeMonthlyAttendanceDTO.builder()
                        .employeeId(e.getId())
                        .employeeNo(e.getEmployeeNo())
                        .employeeName(fullName(e))
                        .department(e.getDepartment() != null ? e.getDepartment().getDepartmentName() : null)
                        .daysRecorded(workingDays)
                        .daysPresent(workingDays)
                        .totalHours(Math.round((regularHours + overtimeHours) * 10.0) / 10.0)
                        .overtimeHours(overtimeHours)
                        .editableOvertime(true) // HR keys overtime manually for no-punch companies
                        .todayStatus(todayIsWorkday ? "PRESENT" : "NOT_CHECKED_IN")
                        .todayCheckIn(null)
                        .todayCheckOut(null)
                        .todayHours(todayIsWorkday ? Math.round(stdHours * 10.0) / 10.0 : 0.0)
                        .build());
            }
            autoRows.sort(Comparator.comparing(r -> r.getEmployeeName() == null ? "" : r.getEmployeeName()));
            return autoRows;
        }

        List<Long> ids = employees.stream().map(Employee::getId).toList();
        Map<Long, List<EmployeeTimesheet>> byEmployee = timesheetRepo
                .findByEmployeeIdInAndAttendanceDateBetween(ids, start, end)
                .stream()
                .collect(Collectors.groupingBy(EmployeeTimesheet::getEmployeeId));

        List<EmployeeMonthlyAttendanceDTO> rows = new ArrayList<>();
        for (Employee e : employees) {
            List<EmployeeTimesheet> records = byEmployee.getOrDefault(e.getId(), List.of());

            int daysRecorded = records.size();
            int daysPresent = (int) records.stream()
                    .filter(t -> resolveWorkedMinutes(t) >= minMinutes)
                    .count();
            long totalMinutes = records.stream().mapToLong(this::resolveWorkedMinutes).sum();
            double totalHours = Math.round(totalMinutes / 60.0 * 10.0) / 10.0;

            // Overtime is computed per day (hours beyond the standard day, capped at
            // the company's daily overtime limit) and summed across the month.
            long overtimeMinutes = records.stream()
                    .mapToLong(t -> {
                        long over = resolveWorkedMinutes(t) - minMinutes;
                        return over <= 0 ? 0L : Math.min(over, otMaxMinutes);
                    })
                    .sum();
            double overtimeHours = Math.round(overtimeMinutes / 60.0 * 10.0) / 10.0;

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
                    .overtimeHours(overtimeHours)
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

    private boolean isWeekday(LocalDate d) {
        // Qatar workweek: Sunday–Thursday (Friday & Saturday are the weekend).
        return d.getDayOfWeek() != DayOfWeek.FRIDAY && d.getDayOfWeek() != DayOfWeek.SATURDAY;
    }

    /** Working days (Sun–Thu) from the 1st of the month through today (or month end if past). */
    private int countWorkingDaysUpToToday(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate today = LocalDate.now();
        LocalDate end = ym.atEndOfMonth().isAfter(today) ? today : ym.atEndOfMonth();
        if (end.isBefore(start)) return 0;
        int count = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (isWeekday(d)) count++;
        }
        return count;
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
