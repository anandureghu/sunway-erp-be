package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeTimesheet;
import com.erp.domain.TimesheetStatus;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.EmployeeTimesheetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Closes attendance sessions an employee forgot to check out of. Any timesheet
 * still {@link TimesheetStatus#CHECKED_IN} on a day that has already ended is
 * auto-checked-out and capped at the company's standard working day (default 6h)
 * from the check-in time, so a missed checkout never inflates worked hours. The
 * row is flagged {@code autoCheckedOut} and annotated so HR can see it wasn't a
 * real punch-out.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoCheckoutJob {

    private static final double DEFAULT_STD_HOURS = 6.0;
    private static final double DEFAULT_OT_MAX_HOURS = 2.0;
    private static final String AUTO_NOTE = "Auto-checkout — employee did not check out.";
    private static final String MAX_SHIFT_NOTE =
            "Auto-checkout — maximum shift (standard + overtime) reached.";

    private final EmployeeTimesheetRepository timesheetRepo;
    private final EmployeeRepository employeeRepo;

    /** Sweep once on startup so any already-stuck sessions get closed immediately. */
    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void closeOnStartup() {
        closeForgottenCheckouts();
    }

    @Transactional
    @Scheduled(cron = "0 10 0 * * *") // every day at 00:10 (after the leave-status job)
    public void closeForgottenCheckouts() {
        LocalDate cutoff = LocalDate.now().minusDays(1); // only days that have fully ended
        List<EmployeeTimesheet> open =
                timesheetRepo.findByStatusAndAttendanceDateLessThanEqual(TimesheetStatus.CHECKED_IN, cutoff);
        if (open.isEmpty()) {
            return;
        }

        Map<Long, Double> stdHoursByCompany = new HashMap<>();
        int closed = 0;

        for (EmployeeTimesheet t : open) {
            if (t.getCheckInTime() == null) {
                continue; // defensive: CHECKED_IN without a check-in time
            }
            double stdHours = resolveStandardHours(t.getEmployeeId(), stdHoursByCompany);
            long stdMinutes = Math.round(stdHours * 60.0);

            t.setCheckOutTime(t.getCheckInTime().plusMinutes(stdMinutes));
            t.setWorkedMinutes(stdMinutes);
            t.setStatus(TimesheetStatus.CHECKED_OUT);
            t.setAutoCheckedOut(true);
            t.setNote(AUTO_NOTE);
            timesheetRepo.save(t);
            closed++;
        }

        log.info("Auto-checkout: closed {} forgotten session(s) up to {}", closed, cutoff);
    }

    /**
     * Intraday sweep: auto-check-out anyone still checked in past their maximum shift
     * (standard hours + overtime cap) plus the company's optional grace minutes.
     * Worked time is capped at the shift limit (grace is unpaid buffer before punch-out)
     * and the row is flagged auto-checked-out.
     */
    @Transactional
    @Scheduled(fixedRate = 60_000) // every minute so grace windows are timely
    public void enforceMaxShift() {
        List<EmployeeTimesheet> open =
                timesheetRepo.findByStatusAndAttendanceDate(TimesheetStatus.CHECKED_IN, LocalDate.now());
        if (open.isEmpty()) {
            return;
        }

        Map<Long, Double> stdCache = new HashMap<>();
        Map<Long, Double> otCache = new HashMap<>();
        Map<Long, Integer> graceCache = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        int closed = 0;

        for (EmployeeTimesheet t : open) {
            if (t.getCheckInTime() == null) {
                continue;
            }
            double capHours = resolveStandardHours(t.getEmployeeId(), stdCache)
                    + resolveOtMax(t.getEmployeeId(), otCache);
            long capMinutes = Math.round(capHours * 60.0);
            int graceMinutes = resolveGrace(t.getEmployeeId(), graceCache);
            long elapsed = Duration.between(t.getCheckInTime(), now).toMinutes();
            // Auto-checkout only after the grace window past the max shift.
            if (elapsed < capMinutes + graceMinutes) {
                continue;
            }

            t.setCheckOutTime(t.getCheckInTime().plusMinutes(capMinutes));
            t.setWorkedMinutes(capMinutes);
            t.setStatus(TimesheetStatus.CHECKED_OUT);
            t.setAutoCheckedOut(true);
            t.setNote(graceMinutes > 0
                    ? MAX_SHIFT_NOTE + " Grace of " + graceMinutes + " minutes applied."
                    : MAX_SHIFT_NOTE);
            timesheetRepo.save(t);
            closed++;
        }

        if (closed > 0) {
            log.info("Max-shift auto-checkout: closed {} session(s) at the shift cap.", closed);
        }
    }

    /** Company standard working hours for the employee, memoised per company. */
    private double resolveStandardHours(Long employeeId, Map<Long, Double> cache) {
        Employee employee = employeeRepo.findById(employeeId).orElse(null);
        if (employee == null || employee.getCompany() == null) {
            return DEFAULT_STD_HOURS;
        }
        Long companyId = employee.getCompany().getId();
        Double cached = cache.get(companyId);
        if (cached != null) {
            return cached;
        }
        double hours = employee.getCompany().getStandardWorkingHoursPerDay() != null
                ? employee.getCompany().getStandardWorkingHoursPerDay().doubleValue()
                : DEFAULT_STD_HOURS;
        cache.put(companyId, hours);
        return hours;
    }

    /** Company overtime cap (hours/day) for the employee, memoised per company. */
    private double resolveOtMax(Long employeeId, Map<Long, Double> cache) {
        Employee employee = employeeRepo.findById(employeeId).orElse(null);
        if (employee == null || employee.getCompany() == null) {
            return DEFAULT_OT_MAX_HOURS;
        }
        Long companyId = employee.getCompany().getId();
        Double cached = cache.get(companyId);
        if (cached != null) {
            return cached;
        }
        double ot = employee.getCompany().getOtMaxHoursPerDay() != null
                ? employee.getCompany().getOtMaxHoursPerDay().doubleValue()
                : DEFAULT_OT_MAX_HOURS;
        cache.put(companyId, ot);
        return ot;
    }

    /** Grace minutes after max shift before auto check-out (0 = none). */
    private int resolveGrace(Long employeeId, Map<Long, Integer> cache) {
        Employee employee = employeeRepo.findById(employeeId).orElse(null);
        if (employee == null || employee.getCompany() == null) {
            return 0;
        }
        Long companyId = employee.getCompany().getId();
        Integer cached = cache.get(companyId);
        if (cached != null) {
            return cached;
        }
        Integer grace = employee.getCompany().getMaxShiftCheckoutGraceMinutes();
        int value = grace != null && grace > 0 ? grace : 0;
        cache.put(companyId, value);
        return value;
    }
}
