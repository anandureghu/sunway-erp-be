package com.erp.dto.leave;

import lombok.Data;

@Data
public class LeavePolicyResponseDTO {

    private Long id;

    private String jobCode;           // e.g. ENG-003
    private String leaveType;         // Annual Leave

    private boolean paid;
    private int defaultDays;

    private boolean genderRestricted;
    private String allowedGender;

    private boolean religionRestricted;
    private String allowedReligion;
}
