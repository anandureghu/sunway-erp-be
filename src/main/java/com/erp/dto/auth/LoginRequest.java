package com.erp.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class LoginRequest {
    @NotBlank private String loginId;
    @NotBlank private String password;
    /** Optional: pick active company when user belongs to multiple tenants */
    private Long preferredCompanyId;
}
