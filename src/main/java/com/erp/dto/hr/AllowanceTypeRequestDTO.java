package com.erp.dto.hr;

import lombok.Data;

@Data
public class AllowanceTypeRequestDTO {

    private String name;

    private String description;

    private Boolean active = true;
}