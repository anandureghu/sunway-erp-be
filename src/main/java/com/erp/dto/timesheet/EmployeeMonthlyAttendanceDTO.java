package com.erp.dto.timesheet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One employee's attendance rollup for a single month — the worked days that
 * feed that month's payroll. Surfaced in the HR "Employee Time Sheets" tab.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeMonthlyAttendanceDTO {
    private Long employeeId;
    private String employeeNo;
    private String employeeName;
    private String department;
    /** Days with any timesheet record (checked in and/or out). */
    private int daysRecorded;
    /** Days actually worked (>= 6h) — the payable worked days. */
    private int daysPresent;
    /** Total worked hours across the month (one decimal). */
    private double totalHours;
    /** Total overtime hours across the month — hours beyond the standard day, daily-capped. */
    private double overtimeHours;
    /**
     * True when this company does not punch in/out, so overtime cannot be derived from
     * timesheets and is instead entered manually per month (editable in the Time Sheets tab).
     */
    private boolean editableOvertime;

    // ── today's live status (only meaningful when the queried month is current) ──
    /** CHECKED_IN | CHECKED_OUT | NOT_CHECKED_IN for today. */
    private String todayStatus;
    private java.time.LocalDateTime todayCheckIn;
    private java.time.LocalDateTime todayCheckOut;
    /** Hours worked today so far (one decimal). */
    private double todayHours;
}
