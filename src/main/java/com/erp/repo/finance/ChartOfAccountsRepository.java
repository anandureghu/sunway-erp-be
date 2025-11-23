package com.erp.repo.finance;

import com.erp.domain.finance.ChartOfAccounts;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChartOfAccountsRepository extends JpaRepository<ChartOfAccounts, Long> {
    Optional<ChartOfAccounts> findByAccountCode(String code);

    List<ChartOfAccounts> findByCompanyId(Long companyId);

    Optional<ChartOfAccounts> findTopByCompanyIdAndType(Long companyId, String type);

    Optional<ChartOfAccounts> findTopByCompanyIdAndTypeAndGlAccountType(Long companyId, String type, String glAccountType);

}
