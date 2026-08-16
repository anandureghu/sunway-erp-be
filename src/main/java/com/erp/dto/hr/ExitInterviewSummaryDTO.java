package com.erp.dto.hr;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/** One row in the company-wide exit / termination interview list (HR Reports). */
@Data
@Builder
public class ExitInterviewSummaryDTO {
    private Long employeeId;
    private String employeeNo;
    private String employeeName;
    private String department;
    private String designation;
    private String employeeStatus;  // employee's exit status (RESIGNED / TERMINATED / RETIRED)
    private String separationType;
    private LocalDate lastWorkingDay;
    private String primaryReason;
    private String status;          // DRAFT | SUBMITTED
    private Instant submittedAt;
    private Instant updatedAt;
}
