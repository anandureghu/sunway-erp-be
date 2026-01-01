package com.erp.repo.finance;

import com.erp.domain.finance.BudgetHeader;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BudgetHeaderRepository extends JpaRepository<BudgetHeader, Long> {
    List<BudgetHeader> findByCompanyId(Long companyId);
}
