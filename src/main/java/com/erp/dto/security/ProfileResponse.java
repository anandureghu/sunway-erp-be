package com.erp.dto.security;

import com.erp.domain.Employee;
import com.erp.domain.User;
import lombok.*;

import java.time.Instant;

@Getter
@Builder
public class ProfileResponse {

    // from User
    private Long    userId;
    private String  fullName;
    private String  username;
    private String  email;
    private String  role;
    private Long    companyRoleId;
    private String  companyRole;
    private Instant createdAt;

    // from Employee (nullable)
    private Long   employeeId;
    private String employeeNo;
    private String firstName;
    private String lastName;
    private String phoneNo;
    private String imageUrl;

    // from Employee → Company
    private Long   companyId;
    private String companyName;

    // from Employee → Department
    private Long   departmentId;
    private String departmentName;

    public static ProfileResponse from(User user, Employee emp) {
        return ProfileResponse.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .companyRoleId(emp != null && emp.getCompanyRoleId() != null
                        ? emp.getCompanyRoleId() : user.getCompanyRoleId())
                .companyRole(emp != null && emp.getCompanyRole() != null
                        ? emp.getCompanyRole() : user.getCompanyRole())
                .createdAt(user.getCreatedAt())
                .employeeId(emp != null ? emp.getId() : null)
                .employeeNo(emp != null ? emp.getEmployeeNo() : null)
                .firstName(emp != null ? emp.getFirstName() : null)
                .lastName(emp != null ? emp.getLastName() : null)
                .phoneNo(emp != null ? emp.getPhoneNo() : null)
                .imageUrl(emp != null ? emp.getImageUrl() : null)
                .companyId(emp != null && emp.getCompany() != null
                        ? emp.getCompany().getId() : null)
                .companyName(emp != null && emp.getCompany() != null
                        ? emp.getCompany().getCompanyName() : null)
                .departmentId(emp != null && emp.getDepartment() != null
                        ? emp.getDepartment().getId() : null)
                .departmentName(emp != null && emp.getDepartment() != null
                        ? emp.getDepartment().getDepartmentName() : null)
                .build();
    }
}
