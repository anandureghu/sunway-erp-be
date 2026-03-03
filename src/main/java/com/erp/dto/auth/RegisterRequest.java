package com.erp.dto.auth;

import com.erp.domain.security.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class RegisterRequest {
    @NotBlank private String fullName;
    @Email @NotBlank private String email;
    @NotBlank private String username;
    @Size(min = 6) @NotBlank private String password;
    private Role role = Role.USER;

}
