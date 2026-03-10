package com.erp.dto.hr;

import com.erp.domain.EmployeeStatus;
import com.erp.domain.security.Role;
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
    // CONTACT DETAILS
    // =======================
    private String phoneNo;
    private String altPhone;

    // =======================
    // RELATIONS
    // =======================
    private Long companyId;
    private Long departmentId;

    // =======================
    // ROLE
    // =======================
    private Role role;

    private String CompanyRole;
}