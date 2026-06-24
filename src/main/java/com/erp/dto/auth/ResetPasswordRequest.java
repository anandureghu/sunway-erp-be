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
@Schema(description = "Complete password reset with the emailed OTP code")
public class ResetPasswordRequest {

    @NotBlank
    @Email
    @Schema(description = "Account email the OTP was sent to", example = "user@example.com")
    private String email;

    @NotBlank
    @Pattern(regexp = "^[0-9]{4,8}$", message = "Code must be 4-8 digits")
    @Schema(description = "Numeric OTP received by email", example = "123456")
    private String code;

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
