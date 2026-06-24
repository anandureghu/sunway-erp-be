package com.erp.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Complete password reset after OTP verification")
public class ResetPasswordRequest {

    @NotBlank
    @Email
    @Schema(description = "Account email used for OTP", example = "user@example.com")
    private String email;

    @NotBlank
    @Schema(
            description = "Verification token from POST /api/auth/otp/verify (purpose PASSWORD_RESET)",
            example = "a1b2c3d4e5f6..."
    )
    private String verificationToken;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 64, message = "Password must be 8–64 characters")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
            message = "Must contain uppercase, lowercase, and a digit"
    )
    private String newPassword;

    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;
}
