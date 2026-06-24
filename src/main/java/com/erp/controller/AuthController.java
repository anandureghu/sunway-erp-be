package com.erp.controller;

import com.erp.domain.User;
import com.erp.dto.auth.*;
import com.erp.service.AuthService;
import com.erp.service.auth.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Authentication", description = "Registration, login (with optional 2FA), token refresh, and password recovery")
@RestController
@RequestMapping("/api/auth")
@SecurityRequirements
public class AuthController {

    private final AuthService auth;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService auth, PasswordResetService passwordResetService) {
        this.auth = auth;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    public ResponseEntity<User> register(@RequestBody @Valid RegisterRequest req) {
        return ResponseEntity.ok(auth.register(req));
    }

    @Operation(
            summary = "Sign in",
            description = """
                    Validates username/email and password. When the user has two-factor authentication
                    enabled in profile security settings, returns requiresTwoFactor with preAuthToken,
                    email, and maskedEmail instead of JWTs. The client must then:
                    1) POST /api/auth/otp/send (LOGIN_2FA) using email from the login response
                    2) POST /api/auth/otp/verify
                    3) POST /api/auth/login/verify-2fa with preAuthToken + verificationToken
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Credentials accepted or 2FA challenge issued",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid credentials")
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest req) {
        return ResponseEntity.ok(auth.login(req));
    }

    @Operation(
            summary = "Complete login after email OTP (2FA)",
            description = "Issues JWT access and refresh tokens after LOGIN_2FA OTP verification."
    )
    @PostMapping("/login/verify-2fa")
    public ResponseEntity<LoginResponse> completeLoginTwoFactor(
            @RequestBody @Valid LoginTwoFactorCompleteRequest req) {
        return ResponseEntity.ok(auth.completeLoginTwoFactor(req));
    }

    @Operation(
            summary = "Forgot password — send OTP",
            description = """
                    Starts password recovery by emailing a one-time code (purpose PASSWORD_RESET).
                    Always returns the same message whether or not the email is registered.
                    Next: POST /api/auth/reset-password with email, code, and new password.
                    """
    )
    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(@RequestBody @Valid ForgotPasswordRequest req) {
        return ResponseEntity.ok(passwordResetService.forgotPassword(req));
    }

    @Operation(
            summary = "Reset password with emailed OTP code",
            description = """
                    Verifies the PASSWORD_RESET OTP and sets a new password in one step.
                    Does not issue a session — user must log in (including 2FA when enabled).
                    """
    )
    @PostMapping("/reset-password")
    public ResponseEntity<ResetPasswordResponse> resetPassword(@RequestBody @Valid ResetPasswordRequest req) {
        return ResponseEntity.ok(passwordResetService.resetPassword(req));
    }

    @PostMapping("/refresh")
    public ResponseEntity<JwtResponse> refresh(@RequestBody @Valid RefreshTokenRequest req) {
        return ResponseEntity.ok(auth.refresh(req.getRefreshToken()));
    }

    @GetMapping("/my-companies")
    public ResponseEntity<List<CompanySummary>> myCompanies() {
        return ResponseEntity.ok(auth.getMyCompanies());
    }

    @PostMapping("/switch-company")
    public ResponseEntity<JwtResponse> switchCompany(@RequestBody @Valid SwitchCompanyRequest req) {
        return ResponseEntity.ok(auth.switchCompany(req.getCompanyId()));
    }

    @GetMapping("/hash")
    public ResponseEntity<String> hash(@RequestParam("raw") String raw) {
        return ResponseEntity.ok(auth.hash(raw));
    }
}
