package com.erp.dto.hr;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AllowanceTypeRequestDTO {

    @NotBlank(message = "Allowance name is required")
    private String name;

    private String description;

    private Boolean active = true;
}