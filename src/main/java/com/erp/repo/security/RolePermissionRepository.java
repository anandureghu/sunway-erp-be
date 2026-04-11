package com.erp.repo.security;

import com.erp.domain.security.HrModule;
import com.erp.domain.security.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    // ✅ Case-insensitive role queries
    List<RolePermission> findByRoleIgnoreCase(String role);

    List<RolePermission> findByRoleIgnoreCaseAndEmployeeIsNull(String role);

    Optional<RolePermission> findByRoleIgnoreCaseAndModule(String role, HrModule module);

    // Employee-specific
    List<RolePermission> findByEmployee_Id(Long employeeId);

    Optional<RolePermission> findByEmployee_IdAndModule(Long employeeId, HrModule module);

    // Delete
    void deleteAllByRoleIgnoreCase(String role);
}