package com.erp.dto.auth;

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

    public JwtResponse() {}

    public JwtResponse(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }
}
