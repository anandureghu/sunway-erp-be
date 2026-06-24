package com.erp.service.security;

import com.erp.domain.Employee;
import com.erp.domain.hr.CompanyRole;
import com.erp.domain.security.CompanyRolePermission;
import com.erp.domain.security.EmployeePermission;
import com.erp.domain.security.EnumRolePermission;
import com.erp.domain.security.AppModule;
import com.erp.domain.security.Role;
import com.erp.dto.security.ModulePermissionDTO;
import com.erp.dto.security.PermissionRecordDTO;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.hr.CompanyRoleRepository;
import com.erp.repo.security.CompanyRolePermissionRepository;
import com.erp.repo.security.EmployeePermissionRepository;
import com.erp.repo.security.EnumRolePermissionRepository;
import com.erp.security.context.AuthContext;
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

    private final EnumRolePermissionRepository enumRolePermissionRepository;
    private final CompanyRolePermissionRepository companyRolePermissionRepository;
    private final EmployeePermissionRepository employeePermissionRepository;
    private final CompanyRoleRepository companyRoleRepository;
    private final EmployeeRepository employeeRepository;
    private final AuthContext authContext;

    @Transactional(readOnly = true)
    public List<PermissionRecordDTO> getEnumRolePermissions(Role role) {
        log.debug("Fetching enum role permissions for role: {}", role);
        return enumRolePermissionRepository.findByRole(role).stream()
                .filter(this::hasAnyPermission)
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionRecordDTO> getCompanyRolePermissions(Long companyRoleId) {
        log.debug("Fetching company role permissions for companyRoleId: {}", companyRoleId);
        requireCompanyRoleAccess(companyRoleId);

        List<CompanyRolePermission> permissions = companyRolePermissionRepository
                .findByCompanyRoleId(companyRoleId);  // ✅ FIX: Use correct method name

        log.debug("Found {} company role permissions", permissions.size());

        return permissions.stream()
                .filter(this::hasAnyPermission)
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionRecordDTO> getEmployeePermissions(Long employeeId) {
        log.debug("Fetching employee permissions for employeeId: {}", employeeId);
        requireEmployeeAccess(employeeId);

        List<EmployeePermission> permissions = employeePermissionRepository
                .findByEmployeeId(employeeId);  // ✅ FIX: Use correct method name

        log.debug("Found {} employee permissions", permissions.size());

        return permissions.stream()
                .filter(this::hasAnyPermission)
                .map(this::toDto)
                .toList();
    }

    /**
     * Get permissions for logged-in user
     * Merges permissions from: EnumRole → CompanyRole → Employee (highest priority wins)
     */
    @Transactional(readOnly = true)
    public List<PermissionRecordDTO> getPermissionsForUser(
            Long employeeId,
            Long companyRoleId,
            Role enumRole
    ) {
        log.info("🔐 getPermissionsForUser called:");
        log.info("   - employeeId: {}", employeeId);
        log.info("   - companyRoleId: {}", companyRoleId);
        log.info("   - enumRole: {}", enumRole);

        Map<AppModule, PermissionRecordDTO> merged = new EnumMap<>(AppModule.class);

        // Priority 1: Start with enum role permissions (lowest priority)
        log.debug("📋 Priority 1: Fetching enum role permissions for role: {}", enumRole);
        List<EnumRolePermission> enumPerms = enumRolePermissionRepository.findByRole(enumRole);
        log.debug("   Found {} enum role permissions", enumPerms.size());

        enumPerms.stream()
                .filter(this::hasAnyPermission)
                .map(this::toDto)
                .forEach(permission -> {
                    log.debug("   + Adding {}: viewOwn={}, viewAll={}",
                            permission.getModule(),
                            permission.isViewOwn(),
                            permission.isViewAll());
                    merged.put(permission.getModule(), permission);
                });

        // Priority 2: Company role permissions (override enum role)
        if (companyRoleId != null && companyRoleId > 0) {
            log.debug("📋 Priority 2: Fetching company role permissions for companyRoleId: {}", companyRoleId);

            List<CompanyRolePermission> companyPerms = companyRolePermissionRepository
                    .findByCompanyRoleId(companyRoleId);  // ✅ FIX: Correct method name

            log.debug("   Found {} company role permissions", companyPerms.size());

            companyPerms.stream()
                    .filter(permission -> permission.getCompanyRole().getActive())  // Only if role is active
                    .filter(CompanyRolePermission::isActive)  // Disabled rules fall through.
                    // Do NOT drop all-false rows here: an explicit row at this
                    // layer is an authoritative override (including a revocation)
                    // and must win over the enum-role grant — matching the
                    // precedence in PermissionCheckService.hasAccess.
                    .map(this::toDto)
                    .forEach(permission -> {
                        log.debug("   + Overriding {}: viewOwn={}, viewAll={}",
                                permission.getModule(),
                                permission.isViewOwn(),
                                permission.isViewAll());
                        merged.put(permission.getModule(), permission);
                    });
        } else {
            log.debug("   Skipped (no companyRoleId provided)");
        }

        // Priority 3: Employee-specific permissions (highest priority - overrides all)
        if (employeeId != null && employeeId > 0) {
            log.debug("📋 Priority 3: Fetching employee-specific permissions for employeeId: {}", employeeId);

            List<EmployeePermission> empPerms = employeePermissionRepository
                    .findByEmployeeId(employeeId);  // ✅ FIX: Correct method name

            log.debug("   Found {} employee-specific permissions", empPerms.size());

            empPerms.stream()
                    .filter(EmployeePermission::isActive)  // Disabled rules fall through.
                    // Employee overrides are authoritative even when all-false:
                    // a per-employee revocation must win over the role grant.
                    .map(this::toDto)
                    .forEach(permission -> {
                        log.debug("   + Overriding (employee-specific) {}: viewOwn={}, viewAll={}",
                                permission.getModule(),
                                permission.isViewOwn(),
                                permission.isViewAll());
                        merged.put(permission.getModule(), permission);
                    });
        } else {
            log.debug("   Skipped (no employeeId provided)");
        }

        // A module whose effective permission ends up all-false (e.g. revoked by
        // an override) is dropped entirely, so the client treats it as no access
        // — consistent with the deny from PermissionCheckService.hasAccess.
        List<PermissionRecordDTO> result = merged.values().stream()
                .filter(this::hasAnyGrant)
                .toList();
        log.info("✅ Total permissions for user: {}", result.size());
        result.forEach(p -> log.debug("   * {}: viewOwn={}, viewAll={}",
                p.getModule(), p.isViewOwn(), p.isViewAll()));

        return result;
    }

    // Upsert semantics: each call inserts-or-updates the modules present in
    // `dtos`. Modules that already have rows but are NOT in the incoming list
    // are preserved — this method intentionally does NOT delete missing rows.
    //
    // Rationale: the older "replace-all" behavior turned an accidental empty
    // or partial list into a destructive wipe of every module's grants for
    // the role. To fully clear a role/employee's permissions, call the
    // explicit DELETE endpoint. To revoke a single module's grant, send a
    // row for that module with all flags false — the read-side
    // `hasAnyPermission` filter will then exclude it.
    public void assignEnumRolePermissions(Role role, List<ModulePermissionDTO> dtos) {
        for (ModulePermissionDTO dto : dtos) {
            if (dto.getModule() == null || dto.getPermission() == null) {
                continue;
            }

            EnumRolePermission permission = enumRolePermissionRepository
                    .findByRoleAndModule(role, dto.getModule())
                    .orElseGet(() -> EnumRolePermission.builder()
                            .role(role)
                            .module(dto.getModule())
                            .build());

            apply(permission, dto.getPermission());
            enumRolePermissionRepository.save(permission);
        }
    }

    public void assignCompanyRolePermissions(Long companyRoleId, List<ModulePermissionDTO> dtos) {
        CompanyRole companyRole = companyRoleRepository.findById(companyRoleId)
                .orElseThrow(() -> new IllegalArgumentException("Company role not found: " + companyRoleId));
        requireCurrentCompany(companyRole.getCompany().getId());

        for (ModulePermissionDTO dto : dtos) {
            if (dto.getModule() == null || dto.getPermission() == null) {
                continue;
            }

            CompanyRolePermission permission = companyRolePermissionRepository
                    .findByCompanyRoleIdAndModule(companyRoleId, dto.getModule())
                    .orElseGet(() -> CompanyRolePermission.builder()
                            .companyRole(companyRole)
                            .module(dto.getModule())
                            .build());

            apply(permission, dto.getPermission());
            companyRolePermissionRepository.save(permission);
        }
    }

    public void assignEmployeePermissions(Long employeeId, List<ModulePermissionDTO> dtos) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));
        requireCurrentCompany(employee.getCompany().getId());

        for (ModulePermissionDTO dto : dtos) {
            if (dto.getModule() == null || dto.getPermission() == null) {
                continue;
            }

            EmployeePermission permission = employeePermissionRepository
                    .findByEmployeeIdAndModule(employeeId, dto.getModule())
                    .orElseGet(() -> EmployeePermission.builder()
                            .employee(employee)
                            .module(dto.getModule())
                            .build());

            apply(permission, dto.getPermission());
            employeePermissionRepository.save(permission);
        }
    }

    /**
     * Enable/disable every permission row for a company role. When inactive the
     * rules are kept but ignored by the resolver (saved but not enforced).
     */
    public void setCompanyRolePermissionsActive(Long companyRoleId, boolean active) {
        requireCompanyRoleAccess(companyRoleId);
        List<CompanyRolePermission> rows =
                companyRolePermissionRepository.findByCompanyRoleId(companyRoleId);
        rows.forEach(row -> row.setActive(active));
        companyRolePermissionRepository.saveAll(rows);
    }

    /** Enable/disable every permission row for an employee override. */
    public void setEmployeePermissionsActive(Long employeeId, boolean active) {
        requireEmployeeAccess(employeeId);
        List<EmployeePermission> rows =
                employeePermissionRepository.findByEmployeeId(employeeId);
        rows.forEach(row -> row.setActive(active));
        employeePermissionRepository.saveAll(rows);
    }

    public void removeEnumRolePermissions(Role role) {
        enumRolePermissionRepository.deleteByRole(role);
    }

    public void removeCompanyRolePermissions(Long companyRoleId) {
        requireCompanyRoleAccess(companyRoleId);
        companyRolePermissionRepository.deleteByCompanyRoleId(companyRoleId);  // ✅ FIX
    }

    public void removeEmployeePermissions(Long employeeId) {
        requireEmployeeAccess(employeeId);
        employeePermissionRepository.deleteByEmployeeId(employeeId);  // ✅ FIX
    }

    private void requireCompanyRoleAccess(Long companyRoleId) {
        CompanyRole companyRole = companyRoleRepository.findById(companyRoleId)
                .orElseThrow(() -> new IllegalArgumentException("Company role not found: " + companyRoleId));
        requireCurrentCompany(companyRole.getCompany().getId());
    }

    private void requireEmployeeAccess(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));
        requireCurrentCompany(employee.getCompany().getId());
    }

    private void requireCurrentCompany(Long companyId) {
        Long currentCompanyId = authContext.getCurrentCompanyId();
        if (currentCompanyId != null && !currentCompanyId.equals(companyId)) {
            throw new IllegalStateException("Not allowed to manage permissions for another company");
        }
    }

    private void apply(EnumRolePermission p, ModulePermissionDTO.PermissionDTO dto) {
        applyCommon(dto, p::setViewOwn, p::setViewAll, p::setCreateOwn, p::setCreateAll,
                p::setEditOwn, p::setEditAll, p::setDeleteOwn, p::setDeleteAll, p::setApprove);
    }

    private void apply(CompanyRolePermission p, ModulePermissionDTO.PermissionDTO dto) {
        applyCommon(dto, p::setViewOwn, p::setViewAll, p::setCreateOwn, p::setCreateAll,
                p::setEditOwn, p::setEditAll, p::setDeleteOwn, p::setDeleteAll, p::setApprove);
    }

    private void apply(EmployeePermission p, ModulePermissionDTO.PermissionDTO dto) {
        applyCommon(dto, p::setViewOwn, p::setViewAll, p::setCreateOwn, p::setCreateAll,
                p::setEditOwn, p::setEditAll, p::setDeleteOwn, p::setDeleteAll, p::setApprove);
    }

    private void applyCommon(
            ModulePermissionDTO.PermissionDTO dto,
            java.util.function.Consumer<Boolean> viewOwn,
            java.util.function.Consumer<Boolean> viewAll,
            java.util.function.Consumer<Boolean> createOwn,
            java.util.function.Consumer<Boolean> createAll,
            java.util.function.Consumer<Boolean> editOwn,
            java.util.function.Consumer<Boolean> editAll,
            java.util.function.Consumer<Boolean> deleteOwn,
            java.util.function.Consumer<Boolean> deleteAll,
            java.util.function.Consumer<Boolean> approve) {
        viewOwn.accept(dto.isViewOwn());
        viewAll.accept(dto.isViewAll());
        createOwn.accept(dto.isCreateOwn());
        createAll.accept(dto.isCreateAll());
        editOwn.accept(dto.isEditOwn());
        editAll.accept(dto.isEditAll());
        deleteOwn.accept(dto.isDeleteOwn());
        deleteAll.accept(dto.isDeleteAll());
        approve.accept(dto.isApprove());
    }

    private boolean hasAnyPermission(EnumRolePermission p) {
        return p.isViewOwn() || p.isViewAll()
                || p.isCreateOwn() || p.isCreateAll()
                || p.isEditOwn() || p.isEditAll()
                || p.isDeleteOwn() || p.isDeleteAll()
                || p.isApprove();
    }

    private boolean hasAnyPermission(CompanyRolePermission p) {
        return p.isViewOwn() || p.isViewAll()
                || p.isCreateOwn() || p.isCreateAll()
                || p.isEditOwn() || p.isEditAll()
                || p.isDeleteOwn() || p.isDeleteAll()
                || p.isApprove();
    }

    private boolean hasAnyPermission(EmployeePermission p) {
        return p.isViewOwn() || p.isViewAll()
                || p.isCreateOwn() || p.isCreateAll()
                || p.isEditOwn() || p.isEditAll()
                || p.isDeleteOwn() || p.isDeleteAll()
                || p.isApprove();
    }

    private boolean hasAnyGrant(PermissionRecordDTO p) {
        return p.isViewOwn() || p.isViewAll()
                || p.isCreateOwn() || p.isCreateAll()
                || p.isEditOwn() || p.isEditAll()
                || p.isDeleteOwn() || p.isDeleteAll()
                || p.isApprove();
    }

    private PermissionRecordDTO toDto(EnumRolePermission p) {
        return PermissionRecordDTO.builder()
                .id(p.getId())
                .module(p.getModule())
                .viewOwn(p.isViewOwn())
                .viewAll(p.isViewAll())
                .createOwn(p.isCreateOwn())
                .createAll(p.isCreateAll())
                .editOwn(p.isEditOwn())
                .editAll(p.isEditAll())
                .deleteOwn(p.isDeleteOwn())
                .deleteAll(p.isDeleteAll())
                .approve(p.isApprove())
                .build();
    }

    private PermissionRecordDTO toDto(CompanyRolePermission p) {
        return PermissionRecordDTO.builder()
                .id(p.getId())
                .module(p.getModule())
                .viewOwn(p.isViewOwn())
                .viewAll(p.isViewAll())
                .createOwn(p.isCreateOwn())
                .createAll(p.isCreateAll())
                .editOwn(p.isEditOwn())
                .editAll(p.isEditAll())
                .deleteOwn(p.isDeleteOwn())
                .deleteAll(p.isDeleteAll())
                .approve(p.isApprove())
                .active(p.isActive())
                .build();
    }

    private PermissionRecordDTO toDto(EmployeePermission p) {
        return PermissionRecordDTO.builder()
                .id(p.getId())
                .module(p.getModule())
                .viewOwn(p.isViewOwn())
                .viewAll(p.isViewAll())
                .createOwn(p.isCreateOwn())
                .createAll(p.isCreateAll())
                .editOwn(p.isEditOwn())
                .editAll(p.isEditAll())
                .deleteOwn(p.isDeleteOwn())
                .deleteAll(p.isDeleteAll())
                .approve(p.isApprove())
                .active(p.isActive())
                .build();
    }
}