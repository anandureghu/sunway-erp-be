package com.erp.dto.hr;

import com.erp.domain.security.Role;
import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeResponseDTO {

    private Long id;
    private String employeeNo;
    private String firstName;
    private String lastName;

    private String gender;
    private String prefix;
    private String status;
    private String maritalStatus;
    private LocalDate dateOfBirth;
    private LocalDate joinDate;

    private String birthplace;
    private String hometown;
    private String nationality;
    private String religion;
    private String identification;

    private String phoneNo;
    private String altPhone;
    private String email;
    private String notes;

    private Long companyId;
    private String companyName;

    private Long departmentId;
    private String departmentName;

    private Long userId;
    private String username;
    private Role   role;         // Spring Security enum → serializes as "USER", "ADMIN" etc.
    private String companyRole;  // ✅ lowercase c — was "CompanyRole" which Jackson serialized wrongly

    private Long companyRoleId;
    private Boolean forcePasswordReset;
    private String  imageUrl;

    /**
     * Job designation derived from the employee's current job assignment
     * (employee_current_job → job_codes.title). Distinct from {@link #companyRole}
     * which represents the security/permission role on the linked User.
     */
    private String designation;
}
