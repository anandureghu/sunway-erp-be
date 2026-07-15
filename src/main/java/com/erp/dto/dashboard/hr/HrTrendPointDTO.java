package com.erp.dto.dashboard.hr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrTrendPointDTO {

    /** ISO month tag like "2025-01". */
    private String yearMonth;

    private long count;
}
