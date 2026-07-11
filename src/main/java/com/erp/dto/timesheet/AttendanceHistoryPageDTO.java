package com.erp.dto.timesheet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A page of the HR attendance-history view for one month. {@code archived}
 * tells the client whether the rows are a frozen snapshot (persistent) or the
 * live computed figures for a not-yet-archived month.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceHistoryPageDTO {
    private List<EmployeeMonthlyAttendanceDTO> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean archived;
}
