package com.erp.dto.auth;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SwitchCompanyRequest {
    @NotNull
    private Long companyId;
}
