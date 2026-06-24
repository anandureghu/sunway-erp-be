package com.erp.controller;

import com.erp.dto.auth.OtpSendRequest;
import com.erp.dto.auth.OtpSendResponse;
import com.erp.dto.auth.OtpVerifyRequest;
import com.erp.dto.auth.OtpVerifyResponse;
import com.erp.domain.auth.OtpPurpose;
import com.erp.service.AuthService;
import com.erp.service.auth.OtpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "OTP / Two-Factor Verification",
        description = "Send and verify email OTP codes for login 2FA, password reset, and other reusable scenarios"
)
@RestController
@RequestMapping("/api/auth/otp")
@RequiredArgsConstructor
@SecurityRequirements
public class OtpController {

    private final OtpService otpService;
    private final AuthService authService;

    @Operation(
            summary = "Send OTP to user email",
            description = """
                    Sends a numeric one-time code to the user's email for the given purpose.
                    Supported purposes: LOGIN_2FA, PASSWORD_RESET, EMAIL_VERIFICATION, SENSITIVE_ACTION.
                    Resend is rate-limited by app.otp.resend-cooldown-seconds.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "OTP dispatch accepted",
                    content = @Content(schema = @Schema(implementation = OtpSendResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Validation error or resend cooldown active")
    })
    @PostMapping("/send")
    public ResponseEntity<OtpSendResponse> sendOtp(@RequestBody @Valid OtpSendRequest request) {
        return ResponseEntity.ok(otpService.sendOtp(request));
    }

    @Operation(
            summary = "Verify OTP code",
            description = """
                    Verifies the OTP sent to the user's email. On success, returns a short-lived
                    verificationToken for follow-up flows (e.g. password reset).

                    When purpose is LOGIN_2FA, also include preAuthToken from POST /api/auth/login;
                    the response includes accessToken and refreshToken. Other purposes do not return JWTs.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "OTP verified",
                    content = @Content(schema = @Schema(implementation = OtpVerifyResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid, expired, or exhausted OTP")
    })
    @PostMapping("/verify")
    public ResponseEntity<OtpVerifyResponse> verifyOtp(@RequestBody @Valid OtpVerifyRequest request) {
        OtpVerifyResponse verified = otpService.verifyOtp(request);
        if (request.getPurpose() == OtpPurpose.LOGIN_2FA) {
            verified = authService.enrichLogin2faOtpVerify(request, verified);
        }
        return ResponseEntity.ok(verified);
    }
}
