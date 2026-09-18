package com.erp.dto.leave;

import lombok.Data;

@Data
public class LeavePolicyRequestDTO {

    private String jobCode;
    private String leaveType;

    private Boolean paid;
    private Integer defaultDays;

    private Boolean genderRestricted;
    private String allowedGender;

    private Boolean religionRestricted;
    private String allowedReligion;
}
