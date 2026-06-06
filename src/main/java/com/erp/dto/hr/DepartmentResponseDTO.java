package com.erp.dto.hr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepartmentResponseDTO {
    private Long id;
    private String departmentCode;
    private String departmentName;

    private Long managerId;
    private String managerFirstName;
    private String managerLastName;

    private Long companyId;
    private String companyName;
    private String companyCode;

    private String description;
}
