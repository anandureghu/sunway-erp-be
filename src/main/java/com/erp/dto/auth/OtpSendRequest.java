package com.erp.dto.auth;

import com.erp.domain.auth.OtpPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request to send a one-time verification code to a user's email")
public class OtpSendRequest {

    @NotBlank
    @Email
    @Schema(description = "Registered user email address", example = "user@example.com")
    private String email;

    @NotNull
    @Schema(
            description = "Scenario that triggered the OTP",
            example = "LOGIN_2FA",
            allowableValues = {"LOGIN_2FA", "PASSWORD_RESET", "EMAIL_VERIFICATION", "SENSITIVE_ACTION"}
    )
    private OtpPurpose purpose;
}
