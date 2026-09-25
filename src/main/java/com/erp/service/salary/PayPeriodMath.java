package com.erp.service.salary;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Calendar-month arithmetic shared by payroll, payslips and the WPS bank file, so a
 * pay period spanning several months is valued the same way everywhere.
 */
public final class PayPeriodMath {

    private PayPeriodMath() {
    }

    /**
     * How many calendar-month equivalents {@code [start, end]} covers: each month the
     * period touches contributes (days of that month inside the period) / (days in that
     * month). Jun 1 – Aug 31 → 3.0; Aug 1 – Aug 31 → 1.0; Aug 16 – Sep 30 → ~1.52.
     */
    public static double monthFactor(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) {
            return 0.0;
        }
        double total = 0.0;
        LocalDate segmentStart = start;
        while (!segmentStart.isAfter(end)) {
            LocalDate monthEnd = segmentStart.withDayOfMonth(segmentStart.lengthOfMonth());
            LocalDate segmentEnd = monthEnd.isBefore(end) ? monthEnd : end;
            long daysInSegment = ChronoUnit.DAYS.between(segmentStart, segmentEnd) + 1;
            total += (double) daysInSegment / segmentStart.lengthOfMonth();
            segmentStart = segmentEnd.plusDays(1);
        }
        return total;
    }

    /** "3 months", "1 month", "1.52 months" — for payslip labels. */
    public static String describeMonths(double factor) {
        double rounded = Math.round(factor * 100.0) / 100.0;
        String number = rounded == Math.rint(rounded)
                ? String.valueOf((long) rounded)
                : String.valueOf(rounded);
        return number + (rounded == 1.0 ? " month" : " months");
    }

    /** True when the period is (essentially) exactly one month's worth. */
    public static boolean isSingleMonth(double factor) {
        return Math.abs(factor - 1.0) < 0.005;
    }
}
