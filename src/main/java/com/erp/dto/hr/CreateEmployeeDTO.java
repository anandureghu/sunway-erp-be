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

    // user/login info
    private String email;
    private String username;
    private String password; // required for creating login (or you can generate one)

    // link
    private Long companyId;
    private Long departmentId;

    // desired role for the user (ADMIN, HR, USER)
    private Role role;
}
