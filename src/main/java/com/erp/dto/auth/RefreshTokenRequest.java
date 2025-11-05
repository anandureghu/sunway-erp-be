package com.erp.dto.auth;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class RefreshTokenRequest {
    @NotBlank private String refreshToken;

}
