package com.erp.dto.hr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateDivisionDTO {
    private String code;
    private String name;
    private String description;
    private Long managerId;
    private Long companyId;
}
