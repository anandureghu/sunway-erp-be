package com.erp.dto.hr;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AllowanceTypeResponseDTO {

    private Long id;
    private String name;
    private String description;
    private boolean active;
}