package com.erp.dto.hr;

import com.erp.domain.Role;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserDetailsDTO {

    // User info
    private Long userId;
    private String fullName;
    private String email;
    private String username;
    private Role role;

    // Employee info (optional)
    private Long employeeId;
    private Long employeeNo;
    private String firstName;
    private String lastName;
    private String phoneNo;

    private Long companyId;
    private String companyName;

    private Long departmentId;
    private String departmentName;
}
