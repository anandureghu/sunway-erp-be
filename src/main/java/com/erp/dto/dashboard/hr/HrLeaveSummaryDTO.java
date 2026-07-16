package com.erp.dto.dashboard.hr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrLeaveSummaryDTO {

    private long totalRequests;
    private long approved;
    private long pending;
    private long rejected;
    private long onLeaveToday;
}
