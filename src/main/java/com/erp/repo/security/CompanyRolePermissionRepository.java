package com.erp.repo.security;

import com.erp.domain.security.CompanyRolePermission;
import com.erp.domain.security.AppModule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyRolePermissionRepository extends JpaRepository<CompanyRolePermission, Long> {

    List<CompanyRolePermission> findByCompanyRole_Id(Long companyRoleId);

    Optional<CompanyRolePermission> findByCompanyRole_IdAndModule(Long companyRoleId, AppModule module);

    Optional<CompanyRolePermission> findByCompanyRole_IdAndCompanyRole_ActiveTrueAndModule(
            Long companyRoleId,
            AppModule module
    );

    void deleteByCompanyRole_Id(Long companyRoleId);

    List<CompanyRolePermission> findByCompanyRoleId(Long companyRoleId);

    Optional<CompanyRolePermission> findByCompanyRoleIdAndModule(Long companyRoleId, AppModule module);

    void deleteByCompanyRoleId(Long companyRoleId);
}
