package com.erp.dto.hr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateDepartmentDTO {
    private String departmentCode;
    private String departmentName;
    private String description;
    private Long managerId;
    private Long divisionId;
}
