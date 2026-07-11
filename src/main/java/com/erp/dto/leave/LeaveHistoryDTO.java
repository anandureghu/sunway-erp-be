package com.erp.dto.leave;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
@Setter
@Getter

public class LeaveHistoryDTO {
    private Long id;
    private Long leaveId;
    private Long employeeId;
    private String employeeName;
    private String leaveCode;
    private String leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate dateReported;
    private LocalDate returnDate;
    private Integer totalDays;
    private Boolean includeWeekends;
    private String supportingDocumentUrl;
    private String leaveStatus;
    private Long delegateId;
    private String delegateName;
    private String rejectionComment;
}
