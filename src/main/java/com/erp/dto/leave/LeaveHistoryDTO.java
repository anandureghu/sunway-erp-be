package com.erp.dto.leave;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
@Setter
@Getter

public class LeaveHistoryDTO {
    private String leaveCode;
    private String leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate dateReported;
    private Integer totalDays;
    private String leaveStatus;
}
