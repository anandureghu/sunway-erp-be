package com.erp.dto.timesheet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One archived monthly attendance snapshot row. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceArchiveRowDTO {
    private Long id;
    private Long employeeId;
    private String employeeNo;
    private String employeeName;
    private String department;
    private int periodYear;
    private int periodMonth;
    private int daysRecorded;
    private int daysPresent;
    private double totalHours;
    private LocalDateTime archivedAt;
}
