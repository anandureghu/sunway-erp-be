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

    // =======================
    // BASIC EMPLOYEE DETAILS
    // =======================
    private String firstName;
    private String lastName;
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

    // =======================
    // CONTACT DETAILS
    // =======================
    private String phoneNo;
    private String altPhone;

    // =======================
    // RELATIONS
    // =======================
    private Long companyId;      // required only for SUPER_ADMIN
    private Long departmentId;

    // =======================
    // ROLE
    // =======================
    private Role role;           // defaults to USER if null
}
