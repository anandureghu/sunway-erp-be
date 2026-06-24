package com.erp.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request to start password recovery via email OTP")
public class ForgotPasswordRequest {

    @NotBlank
    @Email
    @Schema(description = "Account email address", example = "user@example.com")
    private String email;
}
