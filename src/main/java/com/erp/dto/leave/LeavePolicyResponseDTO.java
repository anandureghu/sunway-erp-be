package com.erp.dto.leave;

import lombok.Data;

@Data
public class LeavePolicyResponseDTO {
    private Long id;
    private String leaveType;
    private boolean paid;
    private int defaultDays;
}