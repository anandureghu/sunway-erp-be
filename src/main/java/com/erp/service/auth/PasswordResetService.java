package com.erp.service.auth;

import com.erp.domain.User;
import com.erp.domain.auth.OtpPurpose;
import com.erp.dto.auth.ForgotPasswordRequest;
import com.erp.dto.auth.ForgotPasswordResponse;
import com.erp.dto.auth.OtpSendRequest;
import com.erp.dto.auth.ResetPasswordRequest;
import com.erp.dto.auth.ResetPasswordResponse;
import com.erp.exception.OtpException;
import com.erp.repo.UserRepository;
import com.erp.service.notification.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final OtpService otpService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        OtpSendRequest otpRequest = new OtpSendRequest();
        otpRequest.setEmail(request.getEmail());
        otpRequest.setPurpose(OtpPurpose.PASSWORD_RESET);
        otpService.sendOtp(otpRequest);

        return ForgotPasswordResponse.builder()
                .message("If that account exists, a verification code has been sent to your email")
                .build();
    }

    @Transactional
    public ResetPasswordResponse resetPassword(ResetPasswordRequest request) {
        String email = normalizeEmail(request.getEmail());

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new OtpException("Passwords do not match");
        }

        otpService.verifyAndConsumeOtpCode(email, OtpPurpose.PASSWORD_RESET, request.getCode());

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new OtpException("Invalid or expired verification token"));

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new OtpException("New password must be different from current password");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setForcePasswordReset(false);
        userRepository.save(user);

        sendPasswordChangedEmail(user.getEmail());

        return ResetPasswordResponse.builder()
                .message("Password updated. Please log in.")
                .build();
    }

    private void sendPasswordChangedEmail(String email) {
        if (!emailService.isConfigured()) {
            return;
        }

        String subject = "Your Sunway ERP password was changed";
        String body = """
                Your Sunway ERP account password was changed successfully.

                If you did not make this change, contact your administrator immediately.
                """;
        emailService.sendPlainText(email, subject, body);
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new OtpException("Email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
