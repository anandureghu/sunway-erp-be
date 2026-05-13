package com.erp.service.security;

import com.erp.domain.Employee;
import com.erp.domain.hr.CompanyRole;
import com.erp.domain.security.CompanyRolePermission;
import com.erp.domain.security.EmployeePermission;
import com.erp.domain.security.EnumRolePermission;
import com.erp.domain.security.HrModule;
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

        Map<HrModule, PermissionRecordDTO> merged = new EnumMap<>(HrModule.class);

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
                    .filter(this::hasAnyPermission)
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
                    .filter(this::hasAnyPermission)
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

        List<PermissionRecordDTO> result = merged.values().stream().toList();
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

    private void apply(EnumRolePermission permission, ModulePermissionDTO.PermissionDTO dto) {
        permission.setViewOwn(dto.isViewOwn());
        permission.setViewAll(dto.isViewAll());
        permission.setCreatePermission(dto.isCreate());
        permission.setEditPermission(dto.isEdit());
        permission.setDeletePermission(dto.isDeletePermission());
        permission.setApprove(dto.isApprove());
    }

    private void apply(CompanyRolePermission permission, ModulePermissionDTO.PermissionDTO dto) {
        permission.setViewOwn(dto.isViewOwn());
        permission.setViewAll(dto.isViewAll());
        permission.setCreatePermission(dto.isCreate());
        permission.setEditPermission(dto.isEdit());
        permission.setDeletePermission(dto.isDeletePermission());
        permission.setApprove(dto.isApprove());
    }

    private void apply(EmployeePermission permission, ModulePermissionDTO.PermissionDTO dto) {
        permission.setViewOwn(dto.isViewOwn());
        permission.setViewAll(dto.isViewAll());
        permission.setCreatePermission(dto.isCreate());
        permission.setEditPermission(dto.isEdit());
        permission.setDeletePermission(dto.isDeletePermission());
        permission.setApprove(dto.isApprove());
    }

    private boolean hasAnyPermission(EnumRolePermission permission) {
        return permission.isViewOwn()
                || permission.isViewAll()
                || permission.isCreatePermission()
                || permission.isEditPermission()
                || permission.isDeletePermission()
                || permission.isApprove();
    }

    private boolean hasAnyPermission(CompanyRolePermission permission) {
        return permission.isViewOwn()
                || permission.isViewAll()
                || permission.isCreatePermission()
                || permission.isEditPermission()
                || permission.isDeletePermission()
                || permission.isApprove();
    }

    private boolean hasAnyPermission(EmployeePermission permission) {
        return permission.isViewOwn()
                || permission.isViewAll()
                || permission.isCreatePermission()
                || permission.isEditPermission()
                || permission.isDeletePermission()
                || permission.isApprove();
    }

    private PermissionRecordDTO toDto(EnumRolePermission permission) {
        return PermissionRecordDTO.builder()
                .id(permission.getId())
                .module(permission.getModule())
                .viewOwn(permission.isViewOwn())
                .viewAll(permission.isViewAll())
                .createPermission(permission.isCreatePermission())
                .editPermission(permission.isEditPermission())
                .deletePermission(permission.isDeletePermission())
                .approve(permission.isApprove())
                .build();
    }

    private PermissionRecordDTO toDto(CompanyRolePermission permission) {
        return PermissionRecordDTO.builder()
                .id(permission.getId())
                .module(permission.getModule())
                .viewOwn(permission.isViewOwn())
                .viewAll(permission.isViewAll())
                .createPermission(permission.isCreatePermission())
                .editPermission(permission.isEditPermission())
                .deletePermission(permission.isDeletePermission())
                .approve(permission.isApprove())
                .build();
    }

    private PermissionRecordDTO toDto(EmployeePermission permission) {
        return PermissionRecordDTO.builder()
                .id(permission.getId())
                .module(permission.getModule())
                .viewOwn(permission.isViewOwn())
                .viewAll(permission.isViewAll())
                .createPermission(permission.isCreatePermission())
                .editPermission(permission.isEditPermission())
                .deletePermission(permission.isDeletePermission())
                .approve(permission.isApprove())
                .build();
    }
}