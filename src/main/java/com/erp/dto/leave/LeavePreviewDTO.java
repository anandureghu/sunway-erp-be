package com.erp.dto.leave;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeavePreviewDTO {

    private int totalDays;
    private int currentBalance;
    private int balanceAfterLeave;
}
