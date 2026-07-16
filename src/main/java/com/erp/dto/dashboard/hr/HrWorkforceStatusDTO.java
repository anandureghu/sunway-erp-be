package com.erp.dto.dashboard.hr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrWorkforceStatusDTO {

    private long present;
    private long onLeave;
    private long absent;
    private long total;
}
