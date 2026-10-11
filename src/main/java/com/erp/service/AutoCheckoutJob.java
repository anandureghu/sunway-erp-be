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
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Closes attendance sessions an employee forgot to check out of. Caps and
 * auto-check-out both use the company {@code autoCheckoutAfterHours} policy
 * (8 / 10 / 12), not standard working hours per day.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoCheckoutJob {

    private static final ZoneId DEFAULT_ATTENDANCE_ZONE = ZoneId.of("Asia/Qatar");
    private static final int DEFAULT_AUTO_CHECKOUT_HOURS = 10;
    private static final String AUTO_NOTE = "Auto-checkout — employee did not check out.";
    private static final String MAX_SHIFT_NOTE =
            "Auto-checkout — maximum on-clock time reached.";

    private final EmployeeTimesheetRepository timesheetRepo;
    private final EmployeeRepository employeeRepo;

    private ZoneId resolveZone(Long employeeId, Map<Long, ZoneId> cache) {
        ZoneId cached = cache.get(employeeId);
        if (cached != null) {
            return cached;
        }
        Employee employee = employeeRepo.findById(employeeId).orElse(null);
        ZoneId zone = DEFAULT_ATTENDANCE_ZONE;
        try {
            if (employee != null && employee.getCompany() != null) {
                String tz = employee.getCompany().getTimezone();
                if (tz != null && !tz.isBlank()) {
                    zone = ZoneId.of(tz.trim());
                }
            }
        } catch (Exception ignored) {
            // invalid timezone — keep default
        }
        cache.put(employeeId, zone);
        return zone;
    }

    /** Sweep once on startup so any already-stuck sessions get closed immediately. */
    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void closeOnStartup() {
        closeForgottenCheckouts();
    }

    @Transactional
    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Qatar") // daily sweep; per-row zone still applied
    public void closeForgottenCheckouts() {
        // Load a wide window; each row is evaluated against its company timezone.
        LocalDate globalCutoff = LocalDate.now(DEFAULT_ATTENDANCE_ZONE).plusDays(1);
        List<EmployeeTimesheet> open =
                timesheetRepo.findByStatusAndAttendanceDateLessThanEqual(
                        TimesheetStatus.CHECKED_IN, globalCutoff);
        if (open.isEmpty()) {
            return;
        }

        Map<Long, Integer> hoursCache = new HashMap<>();
        Map<Long, ZoneId> zoneByEmployee = new HashMap<>();
        int closed = 0;

        for (EmployeeTimesheet t : open) {
            if (t.getCheckInTime() == null || t.getAttendanceDate() == null) {
                continue;
            }
            ZoneId zone = resolveZone(t.getEmployeeId(), zoneByEmployee);
            LocalDate companyYesterday = LocalDate.now(zone).minusDays(1);
            if (t.getAttendanceDate().isAfter(companyYesterday)) {
                continue; // still "today" in company zone
            }
            // Cap forgotten overnight sessions at max-shift policy, not standard day length.
            long capMinutes = resolveAutoCheckoutHours(t.getEmployeeId(), hoursCache) * 60L;
            LocalDateTime now = LocalDateTime.now(zone);
            long elapsed = Math.max(0, Duration.between(t.getCheckInTime(), now).toMinutes());
            long paidMinutes = Math.min(elapsed, capMinutes);

            t.setCheckOutTime(t.getCheckInTime().plusMinutes(paidMinutes));
            t.setWorkedMinutes(paidMinutes);
            t.setStatus(TimesheetStatus.CHECKED_OUT);
            t.setAutoCheckedOut(true);
            t.setNote(AUTO_NOTE);
            timesheetRepo.save(t);
            closed++;
        }

        log.info("Auto-checkout: closed {} forgotten session(s)", closed);
    }

    /**
     * Sweep open sessions past the company's max on-clock duration (8 / 10 / 12h),
     * including overnight shifts whose attendance date is no longer "today".
     */
    @Transactional
    @Scheduled(fixedRate = 60_000) // every minute so windows are timely
    public void enforceMaxShift() {
        List<EmployeeTimesheet> open = timesheetRepo.findByStatus(TimesheetStatus.CHECKED_IN);
        if (open.isEmpty()) {
            return;
        }

        Map<Long, Integer> hoursCache = new HashMap<>();
        Map<Long, ZoneId> zoneByEmployee = new HashMap<>();
        int closed = 0;

        for (EmployeeTimesheet t : open) {
            if (t.getCheckInTime() == null) {
                continue;
            }
            ZoneId zone = resolveZone(t.getEmployeeId(), zoneByEmployee);
            LocalDateTime now = LocalDateTime.now(zone);
            long capMinutes = resolveAutoCheckoutHours(t.getEmployeeId(), hoursCache) * 60L;
            long elapsed = Duration.between(t.getCheckInTime(), now).toMinutes();
            if (elapsed < capMinutes) {
                continue;
            }

            t.setCheckOutTime(t.getCheckInTime().plusMinutes(capMinutes));
            t.setWorkedMinutes(capMinutes);
            t.setStatus(TimesheetStatus.CHECKED_OUT);
            t.setAutoCheckedOut(true);
            t.setNote(MAX_SHIFT_NOTE);
            timesheetRepo.save(t);
            closed++;
        }

        if (closed > 0) {
            log.info("Max-shift auto-checkout: closed {} session(s) at the shift cap.", closed);
        }
    }

    /** Fixed auto check-out hours (8 / 10 / 12), memoised per company. */
    private int resolveAutoCheckoutHours(Long employeeId, Map<Long, Integer> cache) {
        Employee employee = employeeRepo.findById(employeeId).orElse(null);
        if (employee == null || employee.getCompany() == null) {
            return DEFAULT_AUTO_CHECKOUT_HOURS;
        }
        Long companyId = employee.getCompany().getId();
        Integer cached = cache.get(companyId);
        if (cached != null) {
            return cached;
        }
        Integer hours = employee.getCompany().getAutoCheckoutAfterHours();
        int value = (hours != null && (hours == 8 || hours == 10 || hours == 12))
                ? hours
                : DEFAULT_AUTO_CHECKOUT_HOURS;
        cache.put(companyId, value);
        return value;
    }
}
