package com.erp.dto.auth;

import com.erp.domain.auth.OtpPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@Schema(description = "OTP verification result with a reusable one-time proof token")
public class OtpVerifyResponse {

    @Schema(description = "Whether the OTP was valid", example = "true")
    private boolean verified;

    @Schema(description = "Verified email address", example = "user@example.com")
    private String email;

    @Schema(description = "OTP scenario", example = "LOGIN_2FA")
    private OtpPurpose purpose;

    @Schema(
            description = "Short-lived token to pass into follow-up flows (password reset, 2FA login, etc.)",
            example = "a1b2c3d4e5f6478990abcdef12345678"
    )
    private String verificationToken;

    @Schema(description = "UTC expiry time for the verification token")
    private Instant verificationTokenExpiresAt;

    @Schema(description = "Human-readable status message", example = "Verification successful")
    private String message;
}
