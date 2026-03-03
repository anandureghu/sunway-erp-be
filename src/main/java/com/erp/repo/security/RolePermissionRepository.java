package com.erp.repo.security;

import com.erp.domain.security.RolePermission;
import com.erp.domain.security.Role;
import com.erp.domain.security.HrModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RolePermissionRepository
        extends JpaRepository<RolePermission, Long> {

    // Find specific module permission for a role
    Optional<RolePermission> findByRoleAndModule(Role role, HrModule module);

    // Get all permissions for a role
    List<RolePermission> findByRole(Role role);

    // ✅ Proper delete query (guaranteed execution)
    @Modifying
    @Query("DELETE FROM RolePermission rp WHERE rp.role = :role")
    void deleteAllByRole(@Param("role") Role role);
}