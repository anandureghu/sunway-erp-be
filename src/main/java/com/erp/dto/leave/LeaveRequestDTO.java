package com.erp.dto.leave;

import lombok.Data;
import java.time.LocalDate;

@Data
public class LeaveRequestDTO {

    private String leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
}
