package com.erp.dto.auth;

import com.erp.dto.subscription.SubscriptionStatusResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "Login result — either JWT tokens or a pending 2FA challenge")
public class LoginResponse {

    @Schema(description = "When true, complete login via POST /api/auth/login/verify-2fa after OTP verify")
    private boolean requiresTwoFactor;

    @Schema(description = "Short-lived token proving password step succeeded (only when requiresTwoFactor is true)")
    private String preAuthToken;

    @Schema(description = "Masked account email for OTP UI (only when requiresTwoFactor is true)")
    private String maskedEmail;

    @Schema(description = "Account email for OTP send/verify calls (only when requiresTwoFactor is true)")
    private String email;

    private String accessToken;
    private String refreshToken;
    private final String tokenType = "Bearer";
    private List<CompanySummary> companies;
    private boolean requiresCompanySelection;
    private SubscriptionStatusResponse subscriptionStatus;

    public LoginResponse() {}

    public static LoginResponse twoFactorRequired(
            String preAuthToken,
            String email,
            String maskedEmail,
            List<CompanySummary> companies,
            boolean requiresCompanySelection
    ) {
        LoginResponse response = new LoginResponse();
        response.requiresTwoFactor = true;
        response.preAuthToken = preAuthToken;
        response.email = email;
        response.maskedEmail = maskedEmail;
        response.companies = companies;
        response.requiresCompanySelection = requiresCompanySelection;
        return response;
    }

    public static LoginResponse authenticated(
            String accessToken,
            String refreshToken,
            List<CompanySummary> companies,
            boolean requiresCompanySelection
    ) {
        return authenticated(accessToken, refreshToken, companies, requiresCompanySelection, null);
    }

    public static LoginResponse authenticated(
            String accessToken,
            String refreshToken,
            List<CompanySummary> companies,
            boolean requiresCompanySelection,
            SubscriptionStatusResponse subscriptionStatus
    ) {
        LoginResponse response = new LoginResponse();
        response.requiresTwoFactor = false;
        response.accessToken = accessToken;
        response.refreshToken = refreshToken;
        response.companies = companies;
        response.requiresCompanySelection = requiresCompanySelection;
        response.subscriptionStatus = subscriptionStatus;
        return response;
    }
}
