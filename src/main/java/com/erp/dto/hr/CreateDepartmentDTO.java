package com.erp.dto.hr;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateDepartmentDTO {
    private String departmentCode;
    private String departmentName;
    private Long managerId;
    private Long companyId; // Instead of full company object
}
