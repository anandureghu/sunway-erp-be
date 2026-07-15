package com.erp.dto.dashboard.hr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrUpcomingEventDTO {

    /** QID_EXPIRY, PASSPORT_EXPIRY, CONTRACT_EXPIRY, WORK_ANNIVERSARY */
    private String type;
    private Long employeeId;
    private String employeeName;
    private LocalDate eventDate;
    private long daysLeft;
}
