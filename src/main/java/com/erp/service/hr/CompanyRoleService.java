package com.erp.service.hr;

import com.erp.domain.hr.Company;
import com.erp.domain.hr.CompanyRole;
import com.erp.dto.hr.CompanyRoleDTO;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.hr.CompanyRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CompanyRoleService {

    private final CompanyRoleRepository roleRepository;
    private final CompanyRepository     companyRepository;

    /* ── List all roles for a company ── */
    @Transactional(readOnly = true)
    public List<CompanyRoleDTO.Response> listByCompany(Long companyId) {
        return roleRepository.findByCompanyId(companyId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /* ── List only active roles ── */
    @Transactional(readOnly = true)
    public List<CompanyRoleDTO.Response> listActiveByCompany(Long companyId) {
        return roleRepository.findByCompanyIdAndActiveTrue(companyId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /* ── Get by ID ── */
    @Transactional(readOnly = true)
    public CompanyRoleDTO.Response getById(Long id) {
        return mapToResponse(findById(id));
    }

    /* ── Create ── */
    public CompanyRoleDTO.Response create(CompanyRoleDTO.Request dto) {

        Company company = findCompany(dto.getCompanyId());

        if (roleRepository.existsByCompanyIdAndName(dto.getCompanyId(), dto.getName().trim())) {
            throw new IllegalStateException(
                    "Role '" + dto.getName() + "' already exists for this company");
        }

        CompanyRole role = CompanyRole.builder()
                .name(dto.getName().trim())
                .description(dto.getDescription() != null ? dto.getDescription().trim() : null)
                .active(dto.getActive() != null ? dto.getActive() : true)
                .company(company)
                .build();

        return mapToResponse(roleRepository.save(role));
    }

    /* ── Update ── */
    public CompanyRoleDTO.Response update(Long id, CompanyRoleDTO.Request dto) {
        CompanyRole role = findById(id);

        if (roleRepository.existsByCompanyIdAndNameAndIdNot(
                role.getCompany().getId(), dto.getName().trim(), id)) {
            throw new IllegalStateException(
                    "Role '" + dto.getName() + "' already exists for this company");
        }

        role.setName(dto.getName().trim());
        role.setDescription(dto.getDescription() != null ? dto.getDescription().trim() : null);
        if (dto.getActive() != null) role.setActive(dto.getActive());

        return mapToResponse(roleRepository.save(role));
    }

    /* ── Toggle active ── */
    public CompanyRoleDTO.Response toggleActive(Long id) {
        CompanyRole role = findById(id);
        role.setActive(!role.getActive());
        return mapToResponse(roleRepository.save(role));
    }

    /* ── Delete ── */
    public void delete(Long id) {
        roleRepository.delete(findById(id));
    }

    /* ── Helpers ── */
    private CompanyRole findById(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + id));
    }

    private Company findCompany(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found: " + companyId));
    }

    private CompanyRoleDTO.Response mapToResponse(CompanyRole role) {
        CompanyRoleDTO.Response res = new CompanyRoleDTO.Response();
        res.setId(role.getId());
        res.setName(role.getName());
        res.setDescription(role.getDescription());
        res.setActive(role.getActive());
        res.setCompanyId(role.getCompany().getId());
        res.setCreatedDate(role.getCreatedDate() != null ? role.getCreatedDate().toString() : null);
        res.setUpdatedDate(role.getUpdatedDate() != null ? role.getUpdatedDate().toString() : null);
        return res;
    }
}