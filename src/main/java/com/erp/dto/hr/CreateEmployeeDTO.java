package com.erp.dto.hr;

import com.erp.domain.EmployeeStatus;
import com.erp.domain.Role;
import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateEmployeeDTO {

    private String employeeNo;
    private String firstName;
    private String lastName;
    private String phoneNo;
    private String gender;
    private String prefix;
    private String maritalStatus;
    private LocalDate dateOfBirth;
    private LocalDate joinDate;

    /**
     * ACTIVE | INACTIVE | ON_LEAVE
     * Optional → defaults handled in service/entity
     */
    private EmployeeStatus status;

    private String notes;
    private String altPhone;

    // user/login info
    private String email;
    private String username;
    private String password;

    // relations
    private Long companyId;
    private Long departmentId;

    // role
    private Role role;
}
