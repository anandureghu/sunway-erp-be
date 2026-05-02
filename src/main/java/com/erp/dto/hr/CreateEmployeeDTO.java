package com.erp.dto.hr;

import com.erp.domain.EmployeeStatus;
import com.erp.domain.security.Role;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    private EmployeeStatus status;

    private String notes;

    // =======================
    // NEW PERSONAL FIELDS
    // =======================
    private String birthplace;
    private String hometown;
    private String nationality;
    private String religion;
    private String identification;


    // =======================
    // RELATIONS
    // =======================
    private Long companyId;
    private Long departmentId;

    // =======================
    // ROLE
    // =======================
    private Role role;
    private Long companyRoleId;

    @JsonAlias("CompanyRole")
    private String companyRole;
}
