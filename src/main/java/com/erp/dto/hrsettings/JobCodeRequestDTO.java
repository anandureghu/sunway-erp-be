package com.erp.dto.hrsettings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JobCodeRequestDTO {

    @NotBlank(message = "Job code is required")
    private String code;

    @NotBlank(message = "Job title is required")
    private String title;

    @NotBlank(message = "Job level is required")
    private String level;

    @NotBlank(message = "Grade is required")
    private String grade;

    @NotNull(message = "Status is required")
    private Boolean active;
}