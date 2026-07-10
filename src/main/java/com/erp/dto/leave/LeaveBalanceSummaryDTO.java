package com.erp.dto.leave;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveBalanceSummaryDTO {
    private String leaveType;
    private boolean paid;
    private Integer totalLeaves;
    private Integer remainingLeaves;
}
