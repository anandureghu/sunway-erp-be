package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeStatus;
import com.erp.domain.EmployeeTimesheet;
import com.erp.domain.TimesheetStatus;
import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.timesheet.AttendanceHistoryItemResponse;
import com.erp.dto.timesheet.MonthlySummaryResponse;
import com.erp.dto.timesheet.TimesheetDashboardResponse;
import com.erp.dto.timesheet.TimesheetTodayResponse;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.EmployeeTimesheetRepository;
import com.erp.security.guard.EmployeeAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

@Service
public class EmployeeTimesheetService {

    /** Default when company timezone is unset / invalid. */
    private static final ZoneId DEFAULT_ATTENDANCE_ZONE = ZoneId.of("Asia/Qatar");

    // Fallbacks used only when a company has no explicit standard-hours setting.
    private static final double DEFAULT_STD_HOURS_PER_DAY = 6.0;
    private static final double DEFAULT_OT_MAX_HOURS = 2.0;

    /** Company's standard full-day length in hours (default 6). */
    private double standardHours(Employee employee) {
        try {
            if (employee.getCompany() != null
                    && employee.getCompany().getStandardWorkingHoursPerDay() != null) {
                return employee.getCompany().getStandardWorkingHoursPerDay().doubleValue();
            }
        } catch (Exception ignored) {
            // lazy company not loadable — fall through to default
        }
        return DEFAULT_STD_HOURS_PER_DAY;
    }

    /** Company's max overtime hours per day (default 2). */
    private double otMaxHours(Employee employee) {
        try {
            if (employee.getCompany() != null
                    && employee.getCompany().getOtMaxHoursPerDay() != null) {
                return employee.getCompany().getOtMaxHoursPerDay().doubleValue();
            }
        } catch (Exception ignored) {
            // lazy company not loadable — fall through to default
        }
        return DEFAULT_OT_MAX_HOURS;
    }

    /** Longest paid shift: standard hours + the overtime cap, in minutes. */
    private long maxShiftMinutes(Employee employee) {
        return Math.round((standardHours(employee) + otMaxHours(employee)) * 60.0);
    }

    /** Whether the company punches in/out (default true). */
    private boolean requireCheckIn(Employee employee) {
        try {
            if (employee.getCompany() != null) {
                return employee.getCompany().isRequireCheckIn();
            }
        } catch (Exception ignored) {
            // lazy company not loadable — assume required
        }
        return true;
    }

    private ZoneId attendanceZone(Employee employee) {
        try {
            if (employee.getCompany() != null) {
                String tz = employee.getCompany().getTimezone();
                if (tz != null && !tz.isBlank()) {
                    return ZoneId.of(tz.trim());
                }
            }
        } catch (Exception ignored) {
            // invalid / lazy — fall through
        }
        return DEFAULT_ATTENDANCE_ZONE;
    }

    private String attendanceTimezoneId(Employee employee) {
        return attendanceZone(employee).getId();
    }

    private LocalDate todayInAttendanceZone(Employee employee) {
        return LocalDate.now(attendanceZone(employee));
    }

    private LocalDateTime nowInAttendanceZone(Employee employee) {
        return LocalDateTime.now(attendanceZone(employee));
    }

    private int checkoutGraceMinutes(Employee employee) {
        try {
            if (employee.getCompany() != null
                    && employee.getCompany().getMaxShiftCheckoutGraceMinutes() != null) {
                return Math.max(0, employee.getCompany().getMaxShiftCheckoutGraceMinutes());
            }
        } catch (Exception ignored) {
            // lazy company not loadable
        }
        return 0;
    }

    private void applyShiftPolicy(TimesheetTodayResponse response, Employee employee) {
        response.setRequireCheckIn(requireCheckIn(employee));
        response.setStandardWorkingHoursPerDay(standardHours(employee));
        response.setOtMaxHoursPerDay(otMaxHours(employee));
        response.setMaxShiftMinutes(maxShiftMinutes(employee));
        response.setMaxShiftCheckoutGraceMinutes(checkoutGraceMinutes(employee));
        response.setTimezone(attendanceTimezoneId(employee));
    }

    private final EmployeeTimesheetRepository repository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeAccessGuard accessGuard;

    public EmployeeTimesheetService(
            EmployeeTimesheetRepository repository,
            EmployeeRepository employeeRepository,
            EmployeeAccessGuard accessGuard) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.accessGuard = accessGuard;
    }

    @Transactional
    public TimesheetTodayResponse checkIn(Long employeeId) {
        // Only active employees may record attendance — a non-active employee
        // (inactive / on leave / resigned …) cannot check in.
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        LocalDate today = todayInAttendanceZone(employee);
        // Self-service: an employee punches their own attendance; HR needs CREATE_ALL.
        accessGuard.assertSelfServiceWrite(employee, AppModule.HR_REPORTS, AppAction.CREATE);
        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            throw new RuntimeException(
                    "Check-in is only available for active employees (current status: "
                            + employee.getStatus() + ").");
        }
        if (!requireCheckIn(employee)) {
            throw new RuntimeException(
                    "Check-in is disabled for your organization — attendance is auto-marked present.");
        }

        EmployeeTimesheet timesheet = repository
                .findByEmployeeIdAndAttendanceDate(employeeId, today)
                .orElseGet(() -> {
                    EmployeeTimesheet t = new EmployeeTimesheet();
                    t.setEmployeeId(employeeId);
                    t.setAttendanceDate(today);
                    t.setStatus(TimesheetStatus.NOT_CHECKED_IN);
                    return t;
                });

        if (timesheet.getCheckInTime() != null) {
            throw new RuntimeException("Employee already checked in today.");
        }

        timesheet.setCheckInTime(nowInAttendanceZone(employee));
        timesheet.setCheckOutTime(null);
        timesheet.setWorkedMinutes(null);
        timesheet.setStatus(TimesheetStatus.CHECKED_IN);

        EmployeeTimesheet saved = repository.save(timesheet);
        TimesheetTodayResponse response = mapToday(saved);
        applyShiftPolicy(response, employee);
        return response;
    }

    @Transactional
    public TimesheetTodayResponse checkOut(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        // Self-service: an employee punches their own attendance; HR needs EDIT_ALL.
        accessGuard.assertSelfServiceWrite(employee, AppModule.HR_REPORTS, AppAction.EDIT);
        if (!requireCheckIn(employee)) {
            throw new RuntimeException(
                    "Check-out is disabled for your organization — attendance is auto-marked present.");
        }

        LocalDate today = todayInAttendanceZone(employee);

        EmployeeTimesheet timesheet = repository
                .findByEmployeeIdAndAttendanceDate(employeeId, today)
                .orElseThrow(() -> new RuntimeException("No check-in found for today."));

        if (timesheet.getCheckInTime() == null) {
            throw new RuntimeException("Cannot checkout without checkin.");
        }

        if (timesheet.getCheckOutTime() != null) {
            throw new RuntimeException("Employee already checked out today.");
        }

        LocalDateTime now = nowInAttendanceZone(employee);
        timesheet.setCheckOutTime(now);
        // Paid minutes are capped at the standard day + overtime cap, so a very long
        // session never records more overtime than the policy allows.
        long worked = calculateWorkedMinutes(timesheet.getCheckInTime(), now);
        timesheet.setWorkedMinutes(Math.min(worked, maxShiftMinutes(employee)));
        timesheet.setStatus(TimesheetStatus.CHECKED_OUT);

        EmployeeTimesheet saved = repository.save(timesheet);
        TimesheetTodayResponse response = mapToday(saved);
        applyShiftPolicy(response, employee);
        return response;
    }

    @Transactional(readOnly = true)
    public TimesheetTodayResponse getToday(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        accessGuard.assertCanRead(employee, AppModule.HR_REPORTS);

        TimesheetTodayResponse response = repository
                .findByEmployeeIdAndAttendanceDate(employeeId, todayInAttendanceZone(employee))
                .map(this::mapToday)
                .orElseGet(() -> {
                    TimesheetTodayResponse r = new TimesheetTodayResponse();
                    r.setEmployeeId(employeeId);
                    r.setDate(todayInAttendanceZone(employee));
                    r.setCheckInTime(null);
                    r.setCheckOutTime(null);
                    r.setWorkedMinutes(0L);
                    r.setWorkedDuration("0m");
                    r.setStatus(TimesheetStatus.NOT_CHECKED_IN.name());
                    return r;
                });
        applyShiftPolicy(response, employee);
        return response;
    }

    @Transactional(readOnly = true)
    public MonthlySummaryResponse getMonthlySummary(Long employeeId, int year, int month) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        accessGuard.assertCanRead(employee, AppModule.HR_REPORTS);

        double stdHours = standardHours(employee);
        MonthlySummaryResponse response = new MonthlySummaryResponse();
        response.setAvgHoursPerDay(stdHours);

        // Companies that don't punch in/out: every working day up to today is present
        // for the standard day (no reliance on timesheet rows).
        if (!requireCheckIn(employee)) {
            int workingDays = countWorkingDaysUpToToday(year, month, employee);
            response.setDaysRecorded(workingDays);
            response.setDaysPresent(workingDays);
            response.setTotalHours(roundToSingleDecimal(workingDays * stdHours));
            return response;
        }

        List<EmployeeTimesheet> records = getMonthlyRecords(employeeId, year, month);
        long minMinutes = Math.round(stdHours * 60.0);

        int daysRecorded = records.size();
        int daysPresent = (int) records.stream()
                .filter(t -> resolveWorkedMinutes(t) >= minMinutes)
                .count();
        long totalMinutes = records.stream()
                .mapToLong(this::resolveWorkedMinutes)
                .sum();

        response.setDaysRecorded(daysRecorded);
        response.setDaysPresent(daysPresent);
        response.setTotalHours(roundToSingleDecimal(totalMinutes / 60.0));

        return response;
    }

    /** Working days (Sun–Thu) from the 1st of the month through today (or month end if past). */
    private int countWorkingDaysUpToToday(int year, int month, Employee employee) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate today = todayInAttendanceZone(employee);
        LocalDate end = ym.atEndOfMonth().isAfter(today) ? today : ym.atEndOfMonth();
        if (end.isBefore(start)) return 0;
        int count = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            // Qatar weekend: Friday & Saturday are off.
            switch (d.getDayOfWeek()) {
                case FRIDAY, SATURDAY -> { }
                default -> count++;
            }
        }
        return count;
    }

    @Transactional(readOnly = true)
    public List<AttendanceHistoryItemResponse> getAttendanceHistory(Long employeeId, int year, int month) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        accessGuard.assertCanRead(employee, AppModule.HR_REPORTS);

        return getMonthlyRecords(employeeId, year, month).stream()
                .sorted(Comparator.comparing(EmployeeTimesheet::getAttendanceDate).reversed())
                .map(this::mapHistory)
                .toList();
    }

    @Transactional(readOnly = true)
    public TimesheetDashboardResponse getDashboard(Long employeeId, int year, int month) {
        TimesheetDashboardResponse response = new TimesheetDashboardResponse();
        response.setToday(getToday(employeeId));
        response.setSummary(getMonthlySummary(employeeId, year, month));
        response.setAttendanceHistory(getAttendanceHistory(employeeId, year, month));
        return response;
    }

    private List<EmployeeTimesheet> getMonthlyRecords(Long employeeId, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();

        return repository.findByEmployeeIdAndAttendanceDateBetween(employeeId, start, end);
    }

    private TimesheetTodayResponse mapToday(EmployeeTimesheet entity) {
        TimesheetTodayResponse response = new TimesheetTodayResponse();
        response.setEmployeeId(entity.getEmployeeId());
        response.setDate(entity.getAttendanceDate());
        response.setCheckInTime(entity.getCheckInTime());
        response.setCheckOutTime(entity.getCheckOutTime());
        response.setWorkedMinutes(resolveWorkedMinutes(entity));
        response.setWorkedDuration(formatMinutes(resolveWorkedMinutes(entity)));
        response.setStatus(entity.getStatus() != null
                ? entity.getStatus().name()
                : TimesheetStatus.NOT_CHECKED_IN.name());
        return response;
    }

    private AttendanceHistoryItemResponse mapHistory(EmployeeTimesheet entity) {
        AttendanceHistoryItemResponse response = new AttendanceHistoryItemResponse();
        response.setAttendanceDate(entity.getAttendanceDate());
        response.setCheckInTime(entity.getCheckInTime());
        response.setCheckOutTime(entity.getCheckOutTime());
        response.setWorkedMinutes(resolveWorkedMinutes(entity));
        response.setWorkedDuration(formatMinutes(resolveWorkedMinutes(entity)));
        response.setStatus(entity.getStatus() != null
                ? entity.getStatus().name()
                : TimesheetStatus.NOT_CHECKED_IN.name());
        response.setAutoCheckedOut(entity.isAutoCheckedOut());
        response.setNote(entity.getNote());
        return response;
    }

    private long resolveWorkedMinutes(EmployeeTimesheet entity) {
        if (entity.getWorkedMinutes() != null && entity.getWorkedMinutes() > 0) {
            return entity.getWorkedMinutes();
        }

        if (entity.getCheckInTime() != null && entity.getCheckOutTime() != null) {
            return calculateWorkedMinutes(entity.getCheckInTime(), entity.getCheckOutTime());
        }

        return 0L;
    }

    private long calculateWorkedMinutes(LocalDateTime checkInTime, LocalDateTime checkOutTime) {
        if (checkInTime == null || checkOutTime == null) {
            return 0L;
        }

        long minutes = Duration.between(checkInTime, checkOutTime).toMinutes();
        return Math.max(minutes, 0L);
    }

    private String formatMinutes(long minutes) {
        if (minutes <= 0) {
            return "0m";
        }

        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;

        if (hours > 0 && remainingMinutes > 0) {
            return hours + "h " + remainingMinutes + "m";
        }

        if (hours > 0) {
            return hours + "h";
        }

        return remainingMinutes + "m";
    }

    private double roundToSingleDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}