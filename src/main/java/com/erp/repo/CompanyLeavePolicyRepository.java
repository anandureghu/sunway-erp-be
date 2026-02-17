package com.erp.repo;

import com.erp.domain.CompanyLeavePolicy;
import com.erp.domain.hr.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyLeavePolicyRepository
        extends JpaRepository<CompanyLeavePolicy, Long> {

    /* ================= BASIC ================= */

    List<CompanyLeavePolicy> findByCompany(Company company);

    /* ================= ROLE BASED ================= */

    List<CompanyLeavePolicy> findByCompanyAndRole(
            Company company,
            String role
    );

    Optional<CompanyLeavePolicy> findByCompanyAndRoleAndLeaveType(
            Company company,
            String role,
            String leaveType
    );

    /* ================= OPTIONAL FILTERS ================= */

    List<CompanyLeavePolicy> findByCompanyAndPaid(
            Company company,
            boolean paid
    );
}
