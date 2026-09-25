package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeLeave;
import com.erp.domain.EmployeeOvertimeOverride;
import com.erp.domain.EmployeeTimesheet;
import com.erp.domain.hr.Company;
import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.timesheet.EmployeeMonthlyAttendanceDTO;
import com.erp.domain.EmployeeStatus;
import com.erp.repo.EmployeeCurrentJobRepo;
import com.erp.repo.EmployeeLeaveRepository;
import com.erp.service.hr.EmployeeSeparationService;
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
import java.util.HashMap;
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
    private final EmployeeLeaveRepository leaveRepo;
    private final EmployeeCurrentJobRepo currentJobRepo;
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

    private List<EmployeeMonthlyAttendanceDTO> summarize(List<Employee> employeesIn, int year, int month) {
        // Everyone still on the books belongs on the timesheet: active, on probation,
        // on leave, and resigned / terminated / retired staff whose separation is not
        // yet complete. Only INACTIVE (exit interview + final settlement done) drops off.
        List<Employee> employees = employeesIn.stream()
                .filter(e -> e.getStatus() != EmployeeStatus.INACTIVE)
                .toList();
        if (employees.isEmpty()) {
            return List.of();
        }

        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        LocalDate today = LocalDate.now();

        // Exiting employees are marked absent; their worked days stop at their last
        // working day (the current job's expected end date), when one is recorded.
        Map<Long, LocalDate> exitLastDay = new HashMap<>();
        for (Employee e : employees) {
            if (EmployeeSeparationService.EXIT_STATUSES.contains(e.getStatus())) {
                currentJobRepo.findByEmployee_Id(e.getId())
                        .map(job -> job.getExpectedEndDate())
                        .ifPresent(d -> exitLastDay.put(e.getId(), d));
            }
        }

        // Approved leaves overlapping the month, grouped by employee. Unpaid-leave
        // working days are dropped from worked days; any leave covering "today" makes
        // the employee show as absent for the day.
        List<Long> rosterIds = employees.stream().map(Employee::getId).toList();
        Map<Long, List<EmployeeLeave>> leavesByEmployee = leaveRepo
                .findApprovedLeavesOverlapping(rosterIds, start, end)
                .stream()
                .collect(Collectors.groupingBy(l -> l.getEmployee().getId()));

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
            // Last day counted toward worked days (clamped to today for the current month).
            LocalDate countEnd = end.isAfter(today) ? today : end;
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

                // An exiting employee only accrues worked days up to their last working day.
                boolean exiting = EmployeeSeparationService.EXIT_STATUSES.contains(e.getStatus());
                LocalDate lastDay = exitLastDay.get(e.getId());
                LocalDate empCountEnd = countEnd;
                if (exiting && lastDay != null && lastDay.isBefore(countEnd)) {
                    empCountEnd = lastDay;
                }
                // A mid-month joiner only accrues worked days from their join date
                // (matches payroll, which prorates the same way).
                LocalDate empCountStart = e.getJoinDate() != null && e.getJoinDate().isAfter(start)
                        ? e.getJoinDate() : start;
                int baseDays = (empCountStart.equals(start) && empCountEnd.equals(countEnd))
                        ? workingDays
                        : LeaveAttendanceUtil.countWorkingDays(empCountStart, empCountEnd);

                // Unpaid-leave working days are absences: drop them from worked days.
                List<EmployeeLeave> leaves = leavesByEmployee.getOrDefault(e.getId(), List.of());
                int unpaidDays = LeaveAttendanceUtil.countUnpaidWorkingDays(leaves, empCountStart, empCountEnd);
                int daysWorked = Math.max(0, baseDays - unpaidDays);
                double regularHours = Math.round(daysWorked * stdHours * 10.0) / 10.0;
                boolean onLeaveToday = todayIsWorkday && LeaveAttendanceUtil.isOnLeave(leaves, today);
                boolean absentToday = exiting && todayIsWorkday;

                autoRows.add(EmployeeMonthlyAttendanceDTO.builder()
                        .employeeId(e.getId())
                        .employeeNo(e.getEmployeeNo())
                        .employeeName(fullName(e))
                        .department(e.getDepartment() != null ? e.getDepartment().getDepartmentName() : null)
                        .employeeStatus(e.getStatus() != null ? e.getStatus().name() : null)
                        .daysRecorded(daysWorked)
                        .daysPresent(daysWorked)
                        .totalHours(Math.round((regularHours + overtimeHours) * 10.0) / 10.0)
                        .overtimeHours(overtimeHours)
                        .editableOvertime(true) // HR keys overtime manually for no-punch companies
                        .todayStatus(absentToday ? "ABSENT"
                                : onLeaveToday ? "ON_LEAVE"
                                : (todayIsWorkday ? "PRESENT" : "NOT_CHECKED_IN"))
                        .todayCheckIn(null)
                        .todayCheckOut(null)
                        .todayHours(absentToday || onLeaveToday ? 0.0
                                : (todayIsWorkday ? Math.round(stdHours * 10.0) / 10.0 : 0.0))
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
            // Days worked counts Sun–Thu only; a Friday/Saturday punch is rest-day
            // overtime (as in payroll), not a working day.
            int daysPresent = (int) records.stream()
                    .filter(t -> t.getAttendanceDate() != null && isWeekday(t.getAttendanceDate()))
                    .filter(t -> resolveWorkedMinutes(t) >= minMinutes)
                    .count();
            long totalMinutes = records.stream().mapToLong(this::resolveWorkedMinutes).sum();
            double totalHours = Math.round(totalMinutes / 60.0 * 10.0) / 10.0;

            // Overtime is computed per day (hours beyond the standard day, capped at
            // the company's daily overtime limit) and summed across the month. Every
            // hour on a rest day is overtime, capped at the maximum working day.
            long overtimeMinutes = records.stream()
                    .mapToLong(t -> {
                        long worked = resolveWorkedMinutes(t);
                        if (t.getAttendanceDate() != null && !isWeekday(t.getAttendanceDate())) {
                            return Math.min(Math.max(worked, 0L), minMinutes + otMaxMinutes);
                        }
                        long over = worked - minMinutes;
                        return over <= 0 ? 0L : Math.min(over, otMaxMinutes);
                    })
                    .sum();
            double overtimeHours = Math.round(overtimeMinutes / 60.0 * 10.0) / 10.0;

            // Today's live status (present only when the queried month is current).
            EmployeeTimesheet todayRec = records.stream()
                    .filter(r -> today.equals(r.getAttendanceDate()))
                    .findFirst()
                    .orElse(null);
            // An exiting employee is absent; one on approved leave today is absent for the day.
            boolean exiting = EmployeeSeparationService.EXIT_STATUSES.contains(e.getStatus());
            List<EmployeeLeave> leaves = leavesByEmployee.getOrDefault(e.getId(), List.of());
            boolean onLeaveToday = LeaveAttendanceUtil.isOnLeave(leaves, today);
            String todayStatus = exiting
                    ? "ABSENT"
                    : onLeaveToday
                    ? "ON_LEAVE"
                    : (todayRec != null && todayRec.getStatus() != null
                        ? todayRec.getStatus().name()
                        : "NOT_CHECKED_IN");
            onLeaveToday = onLeaveToday || exiting;
            double todayHours = onLeaveToday
                    ? 0.0
                    : (todayRec != null
                        ? Math.round(resolveWorkedMinutes(todayRec) / 60.0 * 10.0) / 10.0
                        : 0.0);

            rows.add(EmployeeMonthlyAttendanceDTO.builder()
                    .employeeId(e.getId())
                    .employeeNo(e.getEmployeeNo())
                    .employeeName(fullName(e))
                    .department(e.getDepartment() != null ? e.getDepartment().getDepartmentName() : null)
                    .employeeStatus(e.getStatus() != null ? e.getStatus().name() : null)
                    .daysRecorded(daysRecorded)
                    .daysPresent(daysPresent)
                    .totalHours(totalHours)
                    .overtimeHours(overtimeHours)
                    .todayStatus(todayStatus)
                    .todayCheckIn(onLeaveToday ? null : (todayRec != null ? todayRec.getCheckInTime() : null))
                    .todayCheckOut(onLeaveToday ? null : (todayRec != null ? todayRec.getCheckOutTime() : null))
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
