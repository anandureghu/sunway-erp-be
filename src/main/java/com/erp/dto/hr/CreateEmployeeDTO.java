package com.erp.dto.hr;

import com.erp.domain.Role;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateEmployeeDTO {
    private Long employeeNo;
    private String firstName;
    private String lastName;
    private String phoneNo;
    private Long departmentId;
    private Long companyId;
    private Role role;
}
