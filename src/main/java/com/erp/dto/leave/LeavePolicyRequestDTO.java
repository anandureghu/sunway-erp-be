package com.erp.dto.leave;

import lombok.Data;

@Data
public class LeavePolicyRequestDTO {

    private String role;
    private String leaveType;

    private Boolean paid;
    private Integer defaultDays;

    private Boolean genderRestricted;
    private String allowedGender;
}
