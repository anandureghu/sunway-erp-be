package com.erp.repo.auth;

import com.erp.domain.auth.EmailOtpChallenge;
import com.erp.domain.auth.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface EmailOtpChallengeRepository extends JpaRepository<EmailOtpChallenge, Long> {

    Optional<EmailOtpChallenge> findTopByEmailIgnoreCaseAndPurposeAndVerifiedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            String email,
            OtpPurpose purpose,
            Instant now
    );

    Optional<EmailOtpChallenge> findByVerificationTokenAndVerificationTokenExpiresAtAfter(
            String verificationToken,
            Instant now
    );

    Optional<EmailOtpChallenge> findTopByEmailIgnoreCaseAndPurposeAndVerifiedAtIsNotNullAndVerificationTokenIsNotNullAndVerificationTokenExpiresAtAfterOrderByVerifiedAtDesc(
            String email,
            OtpPurpose purpose,
            Instant now
    );
}
