package com.erp.repo.hr;

import com.erp.domain.hr.CompanyRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyRoleRepository extends JpaRepository<CompanyRole, Long> {

    // All roles for a company
    List<CompanyRole> findByCompanyId(Long companyId);

    // Only active roles
    List<CompanyRole> findByCompanyIdAndActiveTrue(Long companyId);

    // Find by company + name (for duplicate check on create)
    Optional<CompanyRole> findByCompanyIdAndName(Long companyId, String name);

    // Duplicate check on create
    boolean existsByCompanyIdAndName(Long companyId, String name);

    // Duplicate check on update (exclude current role id)
    boolean existsByCompanyIdAndNameAndIdNot(Long companyId, String name, Long id);
}