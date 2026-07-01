package com.erp.dto.security;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateSecuritySettingsRequest {

    @NotNull
    private Boolean twoFactorEnabled;
}
