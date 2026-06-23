package com.erp.dto.auth;

import com.erp.domain.auth.OtpPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "OTP send acknowledgement")
public class OtpSendResponse {

    @Schema(description = "Generic success message", example = "If the account exists, a verification code has been sent to your email")
    private String message;

    @Schema(description = "Masked recipient email", example = "u***@example.com")
    private String maskedEmail;

    @Schema(description = "OTP scenario", example = "LOGIN_2FA")
    private OtpPurpose purpose;

    @Schema(description = "Seconds until the OTP expires", example = "600")
    private long expiresInSeconds;
}
