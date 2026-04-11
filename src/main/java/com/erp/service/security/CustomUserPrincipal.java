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
    private final Role   role;         // Spring Security role (ADMIN, USER, etc.)
    private final String companyRole;  // Company role (Team Lead, HR Manager, etc.)
    private final Long   companyId;    // ✅ REQUIRED for multi-company support

    public CustomUserPrincipal(Long id,
                               String username,
                               String password,
                               Role role,
                               String companyRole,
                               Long companyId) {
        this.id          = id;
        this.username    = username;
        this.password    = password;
        this.role        = role;
        this.companyRole = companyRole;
        this.companyId   = companyId;
    }

    // ─────────────────────────────────────────────
    // 🔹 Getters
    // ─────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    public String getCompanyRole() {
        return companyRole;
    }

    public Long getCompanyId() {
        return companyId;
    }

    /**
     * ✅ Effective role used for permission lookup
     * Priority: companyRole → fallback to enum role
     */
    public String getEffectiveRole() {
        return (companyRole != null && !companyRole.isBlank())
                ? companyRole.trim()
                : role.name();
    }

    // ─────────────────────────────────────────────
    // 🔹 Spring Security
    // ─────────────────────────────────────────────

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(() -> "ROLE_" + role.name());
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
}