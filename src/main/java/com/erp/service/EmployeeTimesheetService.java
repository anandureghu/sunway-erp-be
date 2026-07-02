package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeStatus;
import com.erp.domain.EmployeeTimesheet;
import com.erp.domain.TimesheetStatus;
import com.erp.dto.timesheet.AttendanceHistoryItemResponse;
import com.erp.dto.timesheet.MonthlySummaryResponse;
import com.erp.dto.timesheet.TimesheetDashboardResponse;
import com.erp.dto.timesheet.TimesheetTodayResponse;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.EmployeeTimesheetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

@Service
public class EmployeeTimesheetService {

    private static final double DEFAULT_AVG_HOURS_PER_DAY = 8.0;
    private static final long MIN_WORKED_MINUTES_FOR_DAY = 360L;

    private final EmployeeTimesheetRepository repository;
    private final EmployeeRepository employeeRepository;

    public EmployeeTimesheetService(
            EmployeeTimesheetRepository repository,
            EmployeeRepository employeeRepository) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public TimesheetTodayResponse checkIn(Long employeeId) {
        LocalDate today = LocalDate.now();

        // Only active employees may record attendance — a non-active employee
        // (inactive / on leave / resigned …) cannot check in.
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            throw new RuntimeException(
                    "Check-in is only available for active employees (current status: "
                            + employee.getStatus() + ").");
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

        timesheet.setCheckInTime(LocalDateTime.now());
        timesheet.setCheckOutTime(null);
        timesheet.setWorkedMinutes(null);
        timesheet.setStatus(TimesheetStatus.CHECKED_IN);

        EmployeeTimesheet saved = repository.save(timesheet);
        return mapToday(saved);
    }

    @Transactional
    public TimesheetTodayResponse checkOut(Long employeeId) {
        LocalDate today = LocalDate.now();

        EmployeeTimesheet timesheet = repository
                .findByEmployeeIdAndAttendanceDate(employeeId, today)
                .orElseThrow(() -> new RuntimeException("No check-in found for today."));

        if (timesheet.getCheckInTime() == null) {
            throw new RuntimeException("Cannot checkout without checkin.");
        }

        if (timesheet.getCheckOutTime() != null) {
            throw new RuntimeException("Employee already checked out today.");
        }

        LocalDateTime now = LocalDateTime.now();
        timesheet.setCheckOutTime(now);
        timesheet.setWorkedMinutes(calculateWorkedMinutes(timesheet.getCheckInTime(), now));
        timesheet.setStatus(TimesheetStatus.CHECKED_OUT);

        EmployeeTimesheet saved = repository.save(timesheet);
        return mapToday(saved);
    }

    @Transactional(readOnly = true)
    public TimesheetTodayResponse getToday(Long employeeId) {
        return repository.findByEmployeeIdAndAttendanceDate(employeeId, LocalDate.now())
                .map(this::mapToday)
                .orElseGet(() -> {
                    TimesheetTodayResponse response = new TimesheetTodayResponse();
                    response.setEmployeeId(employeeId);
                    response.setDate(LocalDate.now());
                    response.setCheckInTime(null);
                    response.setCheckOutTime(null);
                    response.setWorkedMinutes(0L);
                    response.setWorkedDuration("0m");
                    response.setStatus(TimesheetStatus.NOT_CHECKED_IN.name());
                    return response;
                });
    }

    @Transactional(readOnly = true)
    public MonthlySummaryResponse getMonthlySummary(Long employeeId, int year, int month) {
        List<EmployeeTimesheet> records = getMonthlyRecords(employeeId, year, month);

        int daysRecorded = records.size();

        int daysPresent = (int) records.stream()
                .filter(this::isPresentRecord)
                .count();

        long totalMinutes = records.stream()
                .mapToLong(this::resolveWorkedMinutes)
                .sum();

        double totalHours = roundToSingleDecimal(totalMinutes / 60.0);

        MonthlySummaryResponse response = new MonthlySummaryResponse();
        response.setDaysRecorded(daysRecorded);
        response.setDaysPresent(daysPresent);
        response.setTotalHours(totalHours);
        response.setAvgHoursPerDay(DEFAULT_AVG_HOURS_PER_DAY);

        return response;
    }

    @Transactional(readOnly = true)
    public List<AttendanceHistoryItemResponse> getAttendanceHistory(Long employeeId, int year, int month) {
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
        return response;
    }

    private boolean isPresentRecord(EmployeeTimesheet entity) {
        return resolveWorkedMinutes(entity) >= MIN_WORKED_MINUTES_FOR_DAY;
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