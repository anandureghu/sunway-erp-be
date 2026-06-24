package com.erp.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Password reset completion acknowledgement")
public class ResetPasswordResponse {

    @Schema(description = "Success message", example = "Password updated. Please log in.")
    private String message;
}
