package com.erp.repo.security;

import com.erp.domain.security.EnumRolePermission;
import com.erp.domain.security.HrModule;
import com.erp.domain.security.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnumRolePermissionRepository extends JpaRepository<EnumRolePermission, Long> {
    List<EnumRolePermission> findByRole(Role role);
    Optional<EnumRolePermission> findByRoleAndModule(Role role, HrModule module);
    void deleteByRole(Role role);
}
