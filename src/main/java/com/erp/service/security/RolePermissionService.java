package com.erp.service.security;

import com.erp.domain.Employee;
import com.erp.domain.security.*;
import com.erp.dto.security.ModulePermissionDTO;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.security.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class RolePermissionService {

    private final RolePermissionRepository repository;
    private final EmployeeRepository       employeeRepository;

    /* ── Get by Role ─────────────────────────────────────────────────────────── */

    /**
     * @param role plain string — "External Auditor", "HR Manager", "USER" etc.
     */
    public List<RolePermission> getByRole(String role) {
        return repository.findByRole(role);
    }

    /* ── Get Permissions for User ────────────────────────────────────────────── */

    /**
     * Employee-specific permissions take priority over role-wide permissions.
     *
     * @param employeeId the employee's ID
     * @param role       plain string role name
     */
    public List<RolePermission> getPermissionsForUser(Long employeeId, String role) {

        // 1. Check employee-specific overrides first
        List<RolePermission> employeePermissions =
                repository.findByEmployee_Id(employeeId);

        if (employeePermissions != null && !employeePermissions.isEmpty()) {
            return employeePermissions.stream()
                    .filter(this::hasAnyPermission)
                    .toList();
        }

        // 2. Fallback to role-wide permissions
        return repository.findByRoleAndEmployeeIsNull(role)
                .stream()
                .filter(this::hasAnyPermission)
                .toList();
    }

    /* ── Assign Permissions ──────────────────────────────────────────────────── */

    /**
     * Upserts permissions for a role or a specific employee.
     * If employeeId is provided → employee-specific override.
     * If employeeId is null    → role-wide rule.
     *
     * @param role       plain string role name
     * @param employeeId optional — null for role-wide
     * @param dtos       list of module permission configs
     */
    public void assignPermissions(String role, Long employeeId, List<ModulePermissionDTO> dtos) {

        Employee employee = null;
        if (employeeId != null) {
            employee = employeeRepository.findById(employeeId)
                    .orElseThrow(() -> new RuntimeException("Employee not found"));
        }

        // Collect incoming modules
        Set<HrModule> incomingModules = new HashSet<>();
        for (ModulePermissionDTO dto : dtos) {
            if (dto.getModule() != null) incomingModules.add(dto.getModule());
        }

        // Load existing permissions
        List<RolePermission> existingPermissions = employeeId != null
                ? repository.findByEmployee_Id(employeeId)
                : repository.findByRole(role);

        // Remove modules no longer present
        for (RolePermission existing : existingPermissions) {
            if (!incomingModules.contains(existing.getModule())) {
                repository.delete(existing);
            }
        }

        // Upsert each module permission
        for (ModulePermissionDTO dto : dtos) {

            if (dto.getModule() == null || dto.getPermission() == null) continue;

            HrModule module = dto.getModule();
            ModulePermissionDTO.PermissionDTO p = dto.getPermission();

            RolePermission permission;

            if (employeeId != null) {
                // Employee-specific: find by employee + module
                permission = repository
                        .findByEmployee_IdAndModule(employeeId, module)
                        .orElse(null);

                if (permission == null) {
                    permission = RolePermission.builder()
                            .role(role)
                            .module(module)
                            .employee(employee)
                            .build();
                }

            } else {
                // Role-wide: find by role string + module
                permission = repository
                        .findByRoleAndModule(role, module)
                        .orElse(null);

                if (permission == null) {
                    permission = RolePermission.builder()
                            .role(role)
                            .module(module)
                            .build();
                }
            }

            permission.setViewOwn(p.isViewOwn());
            permission.setViewAll(p.isViewAll());
            permission.setCreatePermission(p.isCreate());
            permission.setEditPermission(p.isEdit());
            permission.setDeletePermission(p.isDeletePermission());
            permission.setApprove(p.isApprove());

            repository.save(permission);
        }
    }

    /* ── Remove All Permissions for a Role ───────────────────────────────────── */

    /**
     * @param role plain string role name
     */
    public void removeAll(String role) {
        List<RolePermission> permissions = repository.findByRole(role);
        if (!permissions.isEmpty()) {
            repository.deleteAll(permissions);
        }
    }

    /* ── Helper ──────────────────────────────────────────────────────────────── */

    private boolean hasAnyPermission(RolePermission p) {
        return p.isViewOwn()
                || p.isViewAll()
                || p.isCreatePermission()
                || p.isEditPermission()
                || p.isDeletePermission()
                || p.isApprove();
    }
}