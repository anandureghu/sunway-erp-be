package com.erp.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Password recovery request acknowledgement")
public class ForgotPasswordResponse {

    @Schema(
            description = "Generic success message (same whether or not the account exists)",
            example = "If that account exists, a verification code has been sent to your email"
    )
    private String message;
}
