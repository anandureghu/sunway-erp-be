package com.erp.repo.finance;

import com.erp.domain.finance.ChartOfAccounts;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChartOfAccountsRepository extends JpaRepository<ChartOfAccounts, Long> {
    Optional<ChartOfAccounts> findByAccountCode(String code);

    List<ChartOfAccounts> findByCompanyIdOrderByCreatedAtDesc(Long companyId);


}
