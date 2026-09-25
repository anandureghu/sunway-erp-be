package com.erp.service;

import com.erp.domain.EmployeeLeave;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Shared helpers for reconciling approved leave against attendance/timesheets.
 *
 * <p>Unpaid-leave days are absences that do NOT count toward the month's worked
 * days; paid leave still counts. "Unpaid" is keyed off the leave type ("Unpaid
 * Leave"), matching how the leave-policy UI marks a type unpaid.</p>
 */
public final class LeaveAttendanceUtil {

    private LeaveAttendanceUtil() {
    }

    /** True for the "Unpaid Leave" type (case-insensitive, matches any type containing "unpaid"). */
    public static boolean isUnpaidLeaveType(String leaveType) {
        return leaveType != null && leaveType.toUpperCase(Locale.ROOT).contains("UNPAID");
    }

    /** Qatar weekend: Friday & Saturday are the days off. */
    public static boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.FRIDAY || day == DayOfWeek.SATURDAY;
    }

    /** Number of working days (Sun–Thu) in {@code [start, end]}; 0 when the range is empty. */
    public static int countWorkingDays(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) {
            return 0;
        }
        int count = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (!isWeekend(d)) {
                count++;
            }
        }
        return count;
    }

    /** True when {@code date} falls within any of the given (already approved) leave intervals. */
    public static boolean isOnLeave(List<EmployeeLeave> approvedLeaves, LocalDate date) {
        if (approvedLeaves == null || approvedLeaves.isEmpty() || date == null) {
            return false;
        }
        return approvedLeaves.stream().anyMatch(l -> covers(l, date));
    }

    /**
     * Number of WORKING days (Sun–Thu) in {@code [start, end]} covered by an UNPAID
     * approved leave. These are absences excluded from the employee's worked days.
     */
    public static int countUnpaidWorkingDays(List<EmployeeLeave> approvedLeaves, LocalDate start, LocalDate end) {
        if (approvedLeaves == null || approvedLeaves.isEmpty()
                || start == null || end == null || end.isBefore(start)) {
            return 0;
        }
        int count = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (isWeekend(d)) {
                continue;
            }
            final LocalDate day = d;
            boolean unpaid = approvedLeaves.stream()
                    .anyMatch(l -> isUnpaidLeaveType(l.getLeaveType()) && covers(l, day));
            if (unpaid) {
                count++;
            }
        }
        return count;
    }

    private static boolean covers(EmployeeLeave leave, LocalDate date) {
        return leave.getStartDate() != null
                && leave.getEndDate() != null
                && !date.isBefore(leave.getStartDate())
                && !date.isAfter(leave.getEndDate());
    }
}
