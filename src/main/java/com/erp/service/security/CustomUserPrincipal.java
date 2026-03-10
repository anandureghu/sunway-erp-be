package com.erp.service.security;

import com.erp.domain.security.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class CustomUserPrincipal implements UserDetails {

    private final Long   id;
    private final String username;
    private final String password;
    private final Role   role;         // Spring Security — permissions/bypass checks
    private final String companyRole;  // HR-managed — permission table lookups

    public CustomUserPrincipal(Long id,
                               String username,
                               String password,
                               Role role,
                               String companyRole) {
        this.id          = id;
        this.username    = username;
        this.password    = password;
        this.role        = role;
        this.companyRole = companyRole;
    }

    public Long   getId()          { return id; }
    public Role   getRole()        { return role; }
    public String getCompanyRole() { return companyRole; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(() -> "ROLE_" + role.name());
    }

    @Override public String  getPassword()             { return password; }
    @Override public String  getUsername()             { return username; }
    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return true; }
}