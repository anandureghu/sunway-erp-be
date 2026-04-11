package com.erp.service.security;

import com.erp.domain.Employee;
import com.erp.domain.security.*;
import com.erp.dto.security.ModulePermissionDTO;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.security.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RolePermissionService {

    private final RolePermissionRepository repository;
    private final EmployeeRepository employeeRepository;

    // ======================================================
    // GET BY ROLE
    // ======================================================

    public List<RolePermission> getByRole(String role) {
        role = normalizeRole(role);
        return repository.findByRoleIgnoreCaseAndEmployeeIsNull(role);
    }

    // ======================================================
    // GET PERMISSIONS FOR USER (🔥 FIXED MERGE LOGIC)
    // ======================================================

    public List<RolePermission> getPermissionsForUser(Long employeeId, String role) {

        role = normalizeRole(role);

        List<RolePermission> rolePermissions =
                repository.findByRoleIgnoreCaseAndEmployeeIsNull(role);

        List<RolePermission> employeePermissions =
                employeeId != null
                        ? repository.findByEmployee_Id(employeeId)
                        : Collections.emptyList();

        // 🔥 MERGE LOGIC (Employee overrides role)
        Map<HrModule, RolePermission> finalPermissions = new HashMap<>();

        for (RolePermission rp : rolePermissions) {
            finalPermissions.put(rp.getModule(), rp);
        }

        for (RolePermission ep : employeePermissions) {
            finalPermissions.put(ep.getModule(), ep); // override
        }

        return finalPermissions.values().stream()
                .filter(this::hasAnyPermission)
                .toList();
    }

    // ======================================================
    // ASSIGN PERMISSIONS (🔥 FIXED UPSERT + DELETE SAFETY)
    // ======================================================

    public void assignPermissions(String role, Long employeeId, List<ModulePermissionDTO> dtos) {

        role = normalizeRole(role);

        if (role == null) {
            throw new IllegalArgumentException("Role cannot be null");
        }

        Employee employee = null;
        if (employeeId != null) {
            employee = employeeRepository.findById(employeeId)
                    .orElseThrow(() -> new RuntimeException("Employee not found"));
        }

        Set<HrModule> incomingModules = new HashSet<>();
        for (ModulePermissionDTO dto : dtos) {
            if (dto.getModule() != null) {
                incomingModules.add(dto.getModule());
            }
        }

        // ✅ SAFE DELETE (no cross-delete)
        List<RolePermission> existingPermissions =
                employeeId != null
                        ? repository.findByEmployee_Id(employeeId)
                        : repository.findByRoleIgnoreCaseAndEmployeeIsNull(role);

        for (RolePermission existing : existingPermissions) {
            if (!incomingModules.contains(existing.getModule())) {
                repository.delete(existing);
            }
        }

        // ✅ UPSERT
        for (ModulePermissionDTO dto : dtos) {

            if (dto.getModule() == null || dto.getPermission() == null) continue;

            HrModule module = dto.getModule();
            ModulePermissionDTO.PermissionDTO p = dto.getPermission();

            RolePermission permission;

            if (employeeId != null) {
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
                permission = repository
                        .findByRoleIgnoreCaseAndModule(role, module)
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

    // ======================================================
    // REMOVE ALL
    // ======================================================

    public void removeAll(String role) {
        role = normalizeRole(role);
        repository.deleteAllByRoleIgnoreCase(role);
    }

    // ======================================================
    // HELPERS
    // ======================================================

    private String normalizeRole(String role) {
        return (role == null || role.isBlank())
                ? null
                : role.trim().toUpperCase(); // ✅ SYSTEM STANDARD
    }

    private boolean hasAnyPermission(RolePermission p) {
        return p.isViewOwn()
                || p.isViewAll()
                || p.isCreatePermission()
                || p.isEditPermission()
                || p.isDeletePermission()
                || p.isApprove();
    }
}