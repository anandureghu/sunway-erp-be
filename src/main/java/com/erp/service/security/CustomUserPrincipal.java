package com.erp.service.security;

import com.erp.domain.security.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.CredentialsContainer;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public class CustomUserPrincipal implements UserDetails, CredentialsContainer, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final Long employeeId;
    private final String username;
    private String password;
    private final Role role;
    private final Long companyRoleId;
    private final String companyRoleName;
    private final Long companyId;
    private final Set<GrantedAuthority> authorities;

    public CustomUserPrincipal(
            Long userId,
            Long employeeId,
            String username,
            String password,
            Role role,
            Long companyRoleId,
            String companyRoleName,
            Long companyId
    ) {
        this.userId = userId;
        this.employeeId = employeeId;
        this.username = username;
        this.password = password;
        this.role = role;
        this.companyRoleId = companyRoleId;
        this.companyRoleName = companyRoleName;
        this.companyId = companyId;
        this.authorities = Collections.unmodifiableSet(buildAuthorities(role, companyRoleName));
    }

    public static CustomUserPrincipal of(
            Long userId,
            Long employeeId,
            String username,
            String password,
            Role role,
            Long companyRoleId,
            String companyRoleName,
            Long companyId
    ) {
        return new CustomUserPrincipal(
                userId,
                employeeId,
                username,
                password,
                role,
                companyRoleId,
                companyRoleName,
                companyId
        );
    }

    private Set<GrantedAuthority> buildAuthorities(Role role, String companyRoleName) {
        Set<GrantedAuthority> values = new LinkedHashSet<>();

        if (role != null) {
            values.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        }

        if (companyRoleName != null && !companyRoleName.isBlank()) {
            String normalizedCompanyRole = companyRoleName
                    .trim()
                    .toUpperCase()
                    .replace(' ', '_')
                    .replace('-', '_');

            values.add(new SimpleGrantedAuthority("COMPANY_ROLE_" + normalizedCompanyRole));
        }

        return values;
    }

    public Long getId() {
        return userId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public Role getRole() {
        return role;
    }

    public Long getCompanyRoleId() {
        return companyRoleId;
    }

    public String getCompanyRole() {
        return companyRoleName;
    }

    public String getCompanyRoleName() {
        return companyRoleName;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public boolean hasCompanyRole() {
        return companyRoleId != null && companyRoleId > 0;
    }

    public boolean hasEmployee() {
        return employeeId != null && employeeId > 0;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN || role == Role.SUPER_ADMIN;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void eraseCredentials() {
        this.password = null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomUserPrincipal that)) return false;
        return Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    @Override
    public String toString() {
        return "CustomUserPrincipal{" +
                "userId=" + userId +
                ", employeeId=" + employeeId +
                ", username='" + username + '\'' +
                ", role=" + role +
                ", companyRoleId=" + companyRoleId +
                ", companyRoleName='" + companyRoleName + '\'' +
                ", companyId=" + companyId +
                '}';
    }
}