package com.erp.dto.leave;

import lombok.Data;

@Data
public class LeavePolicyRequestDTO {
    private String leaveType;
    private boolean paid;
    private int defaultDays;
}