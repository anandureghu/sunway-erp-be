package com.erp.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class AdminSystemLogResponseDTO {
    private Long id;
    private Instant createdAt;
    private String level;
    private String module;
    private String loggerName;
    private String message;
    private String stackTrace;
    private Long userId;
    private String userEmail;
    private String userUsername;
    private Long companyId;
    private String requestMethod;
    private String requestUri;
}
