package com.erp.service.security;

import com.erp.domain.security.*;
import com.erp.dto.security.ModulePermissionDTO;
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

    public List<RolePermission> getByRole(Role role) {
        return repository.findByRole(role);
    }

    public void assignPermissions(Role role, List<ModulePermissionDTO> dtos) {

        // 1️⃣ Collect incoming modules
        Set<HrModule> incomingModules = new HashSet<>();
        for (ModulePermissionDTO dto : dtos) {
            if (dto.getModule() != null) {
                incomingModules.add(dto.getModule());
            }
        }

        // 2️⃣ Delete permissions NOT in incoming list
        List<RolePermission> existingPermissions = repository.findByRole(role);
        for (RolePermission existing : existingPermissions) {
            if (!incomingModules.contains(existing.getModule())) {
                repository.delete(existing);
            }
        }

        // 3️⃣ Insert or Update
        for (ModulePermissionDTO dto : dtos) {

            if (dto.getModule() == null || dto.getPermission() == null) {
                continue;
            }

            HrModule module = dto.getModule();
            ModulePermissionDTO.PermissionDTO p = dto.getPermission();

            RolePermission permission =
                    repository.findByRoleAndModule(role, module)
                            .orElseGet(() -> RolePermission.builder()
                                    .role(role)
                                    .module(module)
                                    .build());

            permission.setViewOwn(p.isViewOwn());
            permission.setViewAll(p.isViewAll());
            permission.setCreatePermission(p.isCreate());
            permission.setEditPermission(p.isEdit());
            permission.setDeletePermission(p.isDeletePermission());
            permission.setApprove(p.isApprove());

            repository.save(permission);
        }
    }

    // ✅ FIXED REMOVE ALL
    public void removeAll(Role role) {

        List<RolePermission> permissions = repository.findByRole(role);

        if (!permissions.isEmpty()) {
            repository.deleteAll(permissions);
        }
    }
}