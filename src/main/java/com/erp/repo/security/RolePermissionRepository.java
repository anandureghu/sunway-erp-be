package com.erp.repo.security;

import com.erp.domain.security.HrModule;
import com.erp.domain.security.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    // All permissions for a role (including employee overrides)
    List<RolePermission> findByRole(String role);

    // Role-wide permissions only (no employee override)
    List<RolePermission> findByRoleAndEmployeeIsNull(String role);

    // Specific role + module (for upsert on role-wide)
    Optional<RolePermission> findByRoleAndModule(String role, HrModule module);

    // All permissions for a specific employee
    List<RolePermission> findByEmployee_Id(Long employeeId);

    // Specific employee + module (for upsert on employee override)
    Optional<RolePermission> findByEmployee_IdAndModule(Long employeeId, HrModule module);

    // Delete all role-wide permissions for a role
    void deleteAllByRole(String role);
}