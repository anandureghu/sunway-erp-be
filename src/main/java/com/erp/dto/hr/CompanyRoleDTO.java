package com.erp.dto.hr;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// ── Request ──────────────────────────────────────────────────────────────────
public class CompanyRoleDTO {

    @Data
    public static class Request {

        @NotBlank(message = "Role name is required")
        @Size(min = 2, max = 100, message = "Role name must be 2–100 characters")
        private String name;

        private String description;

        private Boolean active = true;

        private Long companyId;
    }

    // ── Response ─────────────────────────────────────────────────────────────
    @Data
    public static class Response {
        private Long    id;
        private String  name;
        private String  description;
        private Boolean active;
        private Long    companyId;
        private String  createdDate;
        private String  updatedDate;
    }
}