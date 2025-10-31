package com.hrmodule.dto.auth;

import lombok.Getter;
import lombok.Setter;

@Getter
public class JwtResponse {
    @Setter
    private String accessToken;
    @Setter
    private String refreshToken;
    private final String tokenType = "Bearer";
    public JwtResponse() {}
    public JwtResponse(String accessToken, String refreshToken) {
        this.accessToken = accessToken; this.refreshToken = refreshToken;
    }

}
