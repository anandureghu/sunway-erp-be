package com.erp.repo;

import com.erp.domain.CompanyLeavePolicy;
import com.erp.domain.hr.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyLeavePolicyRepository
        extends JpaRepository<CompanyLeavePolicy, Long> {

    List<CompanyLeavePolicy> findByCompany(Company company);

    Optional<CompanyLeavePolicy> findByCompanyAndLeaveType(
            Company company, String leaveType);
}
