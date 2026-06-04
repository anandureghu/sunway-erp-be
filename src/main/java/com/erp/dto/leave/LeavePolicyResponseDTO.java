package com.erp.dto.leave;

import lombok.Data;

@Data
public class LeavePolicyResponseDTO {

    private Long id;

    private String role;              // ADMIN, USER
    private String leaveType;         // Annual Leave

    private boolean paid;
    private int defaultDays;

    private boolean genderRestricted;
    private String allowedGender;

    private boolean religionRestricted;
    private String allowedReligion;
}
