package com.erp.dto.hr;

import com.erp.domain.Role;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeResponseDTO {
    private Long id;
    private Long employeeNo;
    private String firstName;
    private String lastName;
    private String phoneNo;

    private Long companyId;
    private String companyName;

    private Long departmentId;
    private String departmentName;

    private Long userId;
    private String username;
    private String email;
    private Role role;
}
