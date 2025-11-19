package com.erp.dto.hr;

import lombok.*;

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
}
