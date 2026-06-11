package com.erp.dto.leave;

import lombok.Data;
import java.time.LocalDate;

@Data
public class LeaveRequestDTO {

    private String leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean includeWeekends;

    /** Optional date the employee heads back to the office after the leave. */
    private LocalDate returnDate;

    /** Optional delegate (same-department colleague) covering during the leave. */
    private Long delegateId;
}
