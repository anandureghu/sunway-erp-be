package com.erp.dto.leave;

import lombok.Data;

import java.time.LocalDate;

/** Body for confirming an employee's return to office on an approved leave. */
@Data
public class LeaveReturnRequest {

    /** The date the employee actually resumed duties at the office. */
    private LocalDate reportedDate;
}
