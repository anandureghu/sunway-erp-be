package com.erp.dto.hr;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignUserToCompanyDTO {
    @NotNull
    private Long userId;
    private Long companyRoleId;
    private String companyRole;
}
