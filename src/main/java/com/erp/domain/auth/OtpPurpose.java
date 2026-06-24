package com.erp.domain.auth;

/**
 * Scenarios that can trigger an email OTP challenge.
 * Downstream flows should verify the returned {@code verificationToken}
 * via {@link com.erp.service.auth.OtpService#consumeVerificationToken}.
 */
public enum OtpPurpose {
    LOGIN_2FA,
    PASSWORD_RESET,
    EMAIL_VERIFICATION,
    SENSITIVE_ACTION
}
