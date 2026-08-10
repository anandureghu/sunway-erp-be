package com.erp.dto.auth;

import com.erp.dto.subscription.SubscriptionStatusResponse;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class JwtResponse {
    private String accessToken;
    private String refreshToken;
    private final String tokenType = "Bearer";
    private List<CompanySummary> companies;
    private boolean requiresCompanySelection;
    private SubscriptionStatusResponse subscriptionStatus;

    public JwtResponse() {}

    public JwtResponse(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }
}
