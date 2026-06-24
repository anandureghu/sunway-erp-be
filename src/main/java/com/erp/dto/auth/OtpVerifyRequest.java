package com.erp.dto.auth;

import com.erp.domain.auth.OtpPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request to verify a one-time code sent to the user's email")
public class OtpVerifyRequest {

    @NotBlank
    @Email
    @Schema(description = "Email the OTP was sent to", example = "user@example.com")
    private String email;

    @NotNull
    @Schema(
            description = "Scenario that triggered the OTP",
            example = "LOGIN_2FA",
            allowableValues = {"LOGIN_2FA", "PASSWORD_RESET", "EMAIL_VERIFICATION", "SENSITIVE_ACTION"}
    )
    private OtpPurpose purpose;

    @NotBlank
    @Pattern(regexp = "^[0-9]{4,8}$", message = "Code must be 4-8 digits")
    @Schema(description = "Numeric OTP received by email", example = "123456")
    private String code;

    @Schema(
            description = "Required when purpose is LOGIN_2FA — pre-auth token from POST /api/auth/login",
            example = "eyJhbGciOiJIUzI1NiJ9..."
    )
    private String preAuthToken;

    @Schema(description = "Optional company to activate when the user belongs to multiple tenants (LOGIN_2FA)")
    private Long preferredCompanyId;
}
