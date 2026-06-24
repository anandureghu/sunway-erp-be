package com.erp.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Complete login after email OTP verification")
public class LoginTwoFactorCompleteRequest {

    @NotBlank
    @Schema(description = "Pre-auth token returned from POST /api/auth/login when 2FA is required")
    private String preAuthToken;

    @NotBlank
    @Schema(description = "Verification token from POST /api/auth/otp/verify (purpose LOGIN_2FA)")
    private String verificationToken;

    @Schema(description = "Optional company to activate when the user belongs to multiple tenants")
    private Long preferredCompanyId;
}
