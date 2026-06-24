package com.erp.service.auth;

import com.erp.domain.auth.EmailOtpChallenge;
import com.erp.domain.auth.OtpPurpose;
import com.erp.dto.auth.OtpSendRequest;
import com.erp.dto.auth.OtpSendResponse;
import com.erp.dto.auth.OtpVerifyRequest;
import com.erp.dto.auth.OtpVerifyResponse;
import com.erp.exception.OtpException;
import com.erp.repo.UserRepository;
import com.erp.repo.auth.EmailOtpChallengeRepository;
import com.erp.service.notification.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailOtpChallengeRepository challengeRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.otp.length:6}")
    private int otpLength;

    @Value("${app.otp.ttl-minutes:10}")
    private int otpTtlMinutes;

    @Value("${app.otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.otp.resend-cooldown-seconds:60}")
    private int resendCooldownSeconds;

    @Value("${app.otp.verification-token-minutes:15}")
    private int verificationTokenMinutes;

    @Transactional
    public OtpSendResponse sendOtp(OtpSendRequest request) {
        String email = normalizeEmail(request.getEmail());
        OtpPurpose purpose = request.getPurpose();

        assertUserExistsWhenRequired(email, purpose);

        Instant now = Instant.now();
        challengeRepository
                .findTopByEmailIgnoreCaseAndPurposeAndVerifiedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                        email, purpose, now
                )
                .ifPresent(existing -> {
                    Instant cooldownEnds = existing.getCreatedAt().plus(resendCooldownSeconds, ChronoUnit.SECONDS);
                    if (now.isBefore(cooldownEnds)) {
                        long secondsLeft = ChronoUnit.SECONDS.between(now, cooldownEnds);
                        throw new OtpException("Please wait " + secondsLeft + " seconds before requesting another OTP");
                    }
                });

        String code = generateOtpCode();
        EmailOtpChallenge challenge = EmailOtpChallenge.builder()
                .email(email)
                .purpose(purpose)
                .codeHash(passwordEncoder.encode(code))
                .attemptCount(0)
                .expiresAt(now.plus(otpTtlMinutes, ChronoUnit.MINUTES))
                .createdAt(now)
                .build();
        challengeRepository.save(challenge);

        sendOtpEmail(email, purpose, code);

        return OtpSendResponse.builder()
                .message("If the account exists, a verification code has been sent to your email")
                .maskedEmail(maskEmail(email))
                .purpose(purpose)
                .expiresInSeconds(otpTtlMinutes * 60L)
                .build();
    }

    @Transactional
    public OtpVerifyResponse verifyOtp(OtpVerifyRequest request) {
        String email = normalizeEmail(request.getEmail());
        OtpPurpose purpose = request.getPurpose();
        Instant now = Instant.now();

        EmailOtpChallenge challenge = validateOtpCode(email, purpose, request.getCode());

        String verificationToken = UUID.randomUUID().toString().replace("-", "");
        challenge.setVerifiedAt(now);
        challenge.setVerificationToken(verificationToken);
        challenge.setVerificationTokenExpiresAt(now.plus(verificationTokenMinutes, ChronoUnit.MINUTES));
        challengeRepository.save(challenge);

        return OtpVerifyResponse.builder()
                .verified(true)
                .email(email)
                .purpose(purpose)
                .verificationToken(verificationToken)
                .verificationTokenExpiresAt(challenge.getVerificationTokenExpiresAt())
                .message("Verification successful")
                .build();
    }

    /**
     * Validates and consumes a one-time verification token issued after a successful OTP verify.
     * Call this from login-2FA or other flows that require prior OTP proof via verification token.
     */
    @Transactional
    public EmailOtpChallenge consumeVerificationToken(String email, OtpPurpose purpose, String verificationToken) {
        String normalizedEmail = normalizeEmail(email);
        Instant now = Instant.now();

        EmailOtpChallenge challenge = challengeRepository
                .findByVerificationTokenAndVerificationTokenExpiresAtAfter(verificationToken, now)
                .orElseThrow(() -> new OtpException("Invalid or expired verification token"));

        if (!challenge.getEmail().equalsIgnoreCase(normalizedEmail) || challenge.getPurpose() != purpose) {
            throw new OtpException("Verification token does not match the requested email or purpose");
        }
        if (challenge.getVerifiedAt() == null) {
            throw new OtpException("Verification token is not valid");
        }

        challenge.setVerificationToken(null);
        challenge.setVerificationTokenExpiresAt(now);
        challengeRepository.save(challenge);
        return challenge;
    }

    /**
     * Validates an OTP code and marks the challenge consumed in one step.
     * Used by password reset so the client does not need a separate verify + token round-trip.
     */
    @Transactional
    public void verifyAndConsumeOtpCode(String email, OtpPurpose purpose, String code) {
        EmailOtpChallenge challenge = validateOtpCode(email, purpose, code);
        Instant now = Instant.now();
        challenge.setVerifiedAt(now);
        challenge.setVerificationToken(null);
        challenge.setVerificationTokenExpiresAt(now);
        challengeRepository.save(challenge);
    }

    private EmailOtpChallenge validateOtpCode(String email, OtpPurpose purpose, String code) {
        String normalizedEmail = normalizeEmail(email);
        String trimmedCode = code.trim();
        Instant now = Instant.now();

        EmailOtpChallenge challenge = challengeRepository
                .findTopByEmailIgnoreCaseAndPurposeAndVerifiedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                        normalizedEmail, purpose, now
                )
                .orElseThrow(() -> new OtpException("Invalid or expired verification code"));

        if (challenge.getAttemptCount() >= maxAttempts) {
            throw new OtpException("Maximum verification attempts exceeded. Request a new code");
        }

        if (!passwordEncoder.matches(trimmedCode, challenge.getCodeHash())) {
            challenge.setAttemptCount(challenge.getAttemptCount() + 1);
            challengeRepository.save(challenge);
            int remaining = maxAttempts - challenge.getAttemptCount();
            throw new OtpException("Invalid verification code. " + remaining + " attempt(s) remaining");
        }

        return challenge;
    }

    private void assertUserExistsWhenRequired(String email, OtpPurpose purpose) {
        if (purpose == OtpPurpose.EMAIL_VERIFICATION) {
            return;
        }
        if (userRepository.findByEmailIgnoreCase(email).isEmpty()) {
            log.info("OTP send skipped for unknown email {} purpose {}", maskEmail(email), purpose);
        }
    }

    private void sendOtpEmail(String email, OtpPurpose purpose, String code) {
        if (userRepository.findByEmailIgnoreCase(email).isEmpty() && purpose != OtpPurpose.EMAIL_VERIFICATION) {
            return;
        }

        String subject = "Your Sunway ERP verification code";
        String body = """
                Your verification code is: %s

                Purpose: %s
                This code expires in %d minutes.

                If you did not request this code, you can ignore this email.
                """.formatted(code, formatPurpose(purpose), otpTtlMinutes);

        if (!emailService.isConfigured()) {
            log.warn("Mail not configured — OTP for {} ({}): {}", maskEmail(email), purpose, code);
            return;
        }

        emailService.sendPlainText(email, subject, body);
    }

    private String generateOtpCode() {
        int bound = (int) Math.pow(10, otpLength);
        int floor = bound / 10;
        int value = floor + RANDOM.nextInt(bound - floor);
        return String.format(Locale.ROOT, "%0" + otpLength + "d", value);
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new OtpException("Email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static String formatPurpose(OtpPurpose purpose) {
        return switch (purpose) {
            case LOGIN_2FA -> "Two-factor login";
            case PASSWORD_RESET -> "Password reset";
            case EMAIL_VERIFICATION -> "Email verification";
            case SENSITIVE_ACTION -> "Sensitive action confirmation";
        };
    }
}
