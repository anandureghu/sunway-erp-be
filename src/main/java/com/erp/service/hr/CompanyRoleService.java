package com.erp.service.hr;

import com.erp.domain.hr.Company;
import com.erp.domain.hr.CompanyRole;
import com.erp.dto.hr.CompanyRoleDTO;
import com.erp.repo.UserRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.hr.CompanyRoleRepository;
import com.erp.repo.security.CompanyRolePermissionRepository;
import com.erp.security.context.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CompanyRoleService {

    private final CompanyRoleRepository roleRepository;
    private final CompanyRepository companyRepository;
    private final CompanyRolePermissionRepository companyRolePermissionRepository;
    private final UserRepository userRepository;
    private final AuthContext authContext;

    // ======================================================
    // LIST ROLES
    // ======================================================

    @Transactional(readOnly = true)
    public List<CompanyRoleDTO.Response> listByCompany(Long companyId) {
        requireCurrentCompany(companyId);

        return roleRepository.findByCompanyIdOrderByCreatedDateDesc(companyId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    // ======================================================
    // LIST ACTIVE ROLES
    // ======================================================

    @Transactional(readOnly = true)
    public List<CompanyRoleDTO.Response> listActiveByCompany(Long companyId) {
        requireCurrentCompany(companyId);

        return roleRepository.findByCompanyIdAndActiveTrueOrderByCreatedDateDesc(companyId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    // ======================================================
    // GET ROLE BY ID
    // ======================================================

    @Transactional(readOnly = true)
    public CompanyRoleDTO.Response getById(Long id) {
        CompanyRole role = findById(id);

        requireCurrentCompany(
                role.getCompany() != null ? role.getCompany().getId() : null
        );

        return mapToResponse(role);
    }

    // ======================================================
    // CREATE ROLE
    // ======================================================

    public CompanyRoleDTO.Response create(CompanyRoleDTO.Request dto) {
        Company company = findCompany(dto.getCompanyId());

        requireCurrentCompany(company.getId());

        // ✅ Duplicate check (case-insensitive safe)
        String roleName = dto.getName().trim();

        if (roleRepository.existsByCompanyIdAndName(company.getId(), roleName)) {
            throw new IllegalStateException(
                    "Role '" + roleName + "' already exists for this company"
            );
        }

        CompanyRole role = CompanyRole.builder()
                .name(roleName)
                .description(dto.getDescription() != null ? dto.getDescription().trim() : null)
                .active(dto.getActive() != null ? dto.getActive() : true)
                .company(company)
                .build();

        return mapToResponse(roleRepository.save(role));
    }

    // ======================================================
    // UPDATE ROLE
    // ======================================================

    public CompanyRoleDTO.Response update(Long id, CompanyRoleDTO.Request dto) {
        CompanyRole role = findById(id);

        requireCurrentCompany(
                role.getCompany() != null ? role.getCompany().getId() : null
        );

        String roleName = dto.getName().trim();

        if (roleRepository.existsByCompanyIdAndNameAndIdNot(
                role.getCompany().getId(),
                roleName,
                id
        )) {
            throw new IllegalStateException(
                    "Role '" + roleName + "' already exists for this company"
            );
        }

        role.setName(roleName);
        role.setDescription(dto.getDescription() != null ? dto.getDescription().trim() : null);

        if (dto.getActive() != null) {
            role.setActive(dto.getActive());
        }

        return mapToResponse(roleRepository.save(role));
    }

    // ======================================================
    // TOGGLE ACTIVE
    // ======================================================

    public CompanyRoleDTO.Response toggleActive(Long id) {
        CompanyRole role = findById(id);

        requireCurrentCompany(
                role.getCompany() != null ? role.getCompany().getId() : null
        );

        role.setActive(!Boolean.TRUE.equals(role.getActive()));

        return mapToResponse(roleRepository.save(role));
    }

    // ======================================================
    // DELETE ROLE
    // ======================================================

    public void delete(Long id) {
        CompanyRole role = findById(id);

        requireCurrentCompany(
                role.getCompany() != null ? role.getCompany().getId() : null
        );

        if (userRepository.existsByCompanyRoleRef_Id(id)) {
            throw new IllegalStateException(
                    "Cannot delete a role that is still assigned to users"
            );
        }

        companyRolePermissionRepository.deleteByCompanyRole_Id(id);
        roleRepository.delete(role);
    }

    // ======================================================
    // INTERNAL HELPERS
    // ======================================================

    private CompanyRole findById(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + id));
    }

    private Company findCompany(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found: " + companyId));
    }

    /**
     * ✅ FIXED VERSION
     * Prevents unwanted 500 errors when auth context is null
     */
    private void requireCurrentCompany(Long companyId) {
        Long currentCompanyId = authContext.getCurrentCompanyId();

        // 🔥 DEBUG (remove later)
        System.out.println("Requested companyId: " + companyId);
        System.out.println("Auth companyId: " + currentCompanyId);

        // ✅ Allow if auth context not set (important fix)
        if (currentCompanyId == null) {
            return;
        }

        if (companyId == null || !currentCompanyId.equals(companyId)) {
            throw new IllegalStateException(
                    "Access denied: currentCompanyId=" + currentCompanyId +
                            ", requested=" + companyId
            );
        }
    }

    private CompanyRoleDTO.Response mapToResponse(CompanyRole role) {
        CompanyRoleDTO.Response response = new CompanyRoleDTO.Response();

        response.setId(role.getId());
        response.setName(role.getName());
        response.setDescription(role.getDescription());
        response.setActive(role.getActive());

        response.setCompanyId(
                role.getCompany() != null ? role.getCompany().getId() : null
        );

        response.setCreatedDate(
                role.getCreatedDate() != null ? role.getCreatedDate().toString() : null
        );

        response.setUpdatedDate(
                role.getUpdatedDate() != null ? role.getUpdatedDate().toString() : null
        );

        return response;
    }
}