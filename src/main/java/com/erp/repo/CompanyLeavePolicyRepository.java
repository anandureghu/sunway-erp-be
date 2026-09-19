package com.erp.repo;

import com.erp.domain.CompanyLeavePolicy;
import com.erp.domain.hr.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyLeavePolicyRepository
        extends JpaRepository<CompanyLeavePolicy, Long> {

    /* ================= BASIC ================= */

    List<CompanyLeavePolicy> findByCompanyOrderByIdDesc(Company company);

    /* ================= JOB CODE BASED ================= */

    List<CompanyLeavePolicy> findByCompanyAndJobCode(
            Company company,
            String jobCode
    );

    Optional<CompanyLeavePolicy> findByCompanyAndJobCodeAndLeaveType(
            Company company,
            String jobCode,
            String leaveType
    );

    /* ================= OPTIONAL FILTERS ================= */

    List<CompanyLeavePolicy> findByCompanyAndPaid(
            Company company,
            boolean paid
    );
}
